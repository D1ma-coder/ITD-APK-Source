package com.itd.app.data.analytics

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.itd.app.util.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Tracks photo opens and video watch time, sending batches to /api/v1/x. */
class InteractionTracker(
    private val api: AnalyticsApi,
    private val debugStore: AnalyticsDebugStore,
    private val sessionId: String,
    internal val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val pending = ArrayList<InteractionEvent>(BATCH_SIZE)
    private var flushScheduled = false
    private val flushRunnable = Runnable { flush() }
    private val videos = HashMap<Player, VideoSession>()

    fun record(event: InteractionEvent) {
        val full = synchronized(lock) {
            pending.add(event)
            pending.size >= BATCH_SIZE
        }
        debugStore.recordInteraction(
            InteractionLogEntry(
                type = event.type,
                postId = event.postId.orEmpty(),
                source = event.source,
                attachmentId = when (event) {
                    is InteractionEvent.PhotoOpen -> event.attachmentId.orEmpty()
                    is InteractionEvent.VideoProgress -> event.attachmentId
                },
                mediaIdx = (event as? InteractionEvent.PhotoOpen)?.mediaIdx,
                playedMs = (event as? InteractionEvent.VideoProgress)?.playedMs,
                durationMs = (event as? InteractionEvent.VideoProgress)?.durationMs,
                loggedAt = clock(),
            ),
        )
        if (full) {
            flush()
            return
        }
        synchronized(lock) {
            if (flushScheduled) return
            flushScheduled = true
            handler.postDelayed(flushRunnable, FLUSH_DELAY_MS)
        }
    }

    fun startVideo(player: Player, postId: String, vs: String?, attachmentId: String, source: ImpressionSource) {
        if (vs.isNullOrEmpty() || attachmentId.isEmpty()) {
            LogTag.ANALYTICS.tree().w("skip video tracking post=%s", postId.take(8))
            return
        }
        synchronized(lock) {
            if (videos.containsKey(player)) return
            val session = VideoSession(player, postId, vs, attachmentId, source)
            videos[player] = session
            session.start()
            LogTag.ANALYTICS.tree().d("video start post=%s attachment=%s", postId.take(8), attachmentId.take(8))
        }
    }

    fun stopVideo(player: Player) {
        val session = synchronized(lock) { videos.remove(player) } ?: return
        val event = session.finish()
        if (event != null) {
            LogTag.ANALYTICS.tree().d("video stop post=%s", event.postId.take(8))
            record(event)
        } else {
            LogTag.ANALYTICS.tree().d("video skip post=%s", session.postId.take(8))
        }
    }

    fun stopAll(sync: Boolean) {
        val sessions = synchronized(lock) { videos.values.toList().also { videos.clear() } }
        sessions.forEach { session -> session.finish()?.let(::record) }
        if (!sync) {
            flush()
            return
        }
        val batch = synchronized(lock) {
            flushScheduled = false
            handler.removeCallbacks(flushRunnable)
            if (pending.isEmpty()) return
            pending.toList().also { pending.clear() }
        }
        try {
            runBlocking { send(batch) }
        } catch (_: Exception) {
            LogTag.ANALYTICS.tree().w("sync interaction flush failed")
        }
    }

    fun flush() {
        synchronized(lock) {
            flushScheduled = false
            handler.removeCallbacks(flushRunnable)
            if (pending.isEmpty()) return
            val batch = pending.toList()
            pending.clear()
            scope.launch { send(batch) }
        }
    }

    private suspend fun send(events: List<InteractionEvent>) {
        try {
            if (events.isNotEmpty()) api.sendInteractions(InteractionBatchWire(sessionId, events.map(::toWire)))
        } catch (_: Exception) {
            LogTag.ANALYTICS.tree().w("interaction batch failed")
        }
    }

    private inner class VideoSession(
        val player: Player,
        val postId: String,
        val vs: String,
        val attachmentId: String,
        val source: ImpressionSource,
    ) {
        private var playedMs = 0L
        private var playingSince: Long? = null
        private var durationMs = 0L
        private val tickHandler = Handler(Looper.getMainLooper())
        private val tick = object : Runnable {
            override fun run() {
                if (player.isPlaying) {
                    val now = clock()
                    playingSince?.let { playedMs += now - it }
                    playingSince = now
                }
                updateDuration()
                tickHandler.postDelayed(this, TICK_MS)
            }
        }
        private val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateDuration()
                if (!isPlaying) pause() else if (playingSince == null) playingSince = clock()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateDuration()
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (player.isPlaying && playingSince == null) playingSince = clock()
            }
        }

        fun start() {
            player.addListener(listener)
            updateDuration()
            if (player.isPlaying) playingSince = clock()
            tickHandler.postDelayed(tick, TICK_MS)
        }

        fun finish(): InteractionEvent.VideoProgress? {
            tickHandler.removeCallbacks(tick)
            updateDuration()
            try {
                player.removeListener(listener)
            } catch (_: Exception) {
                LogTag.ANALYTICS.tree().w("remove player listener failed")
            }
            pause()
            if (durationMs <= 0 || playedMs < MIN_PLAYED_MS) return null
            return InteractionEvent.VideoProgress(postId, vs, attachmentId, source, playedMs, durationMs)
        }

        private fun updateDuration() {
            val duration = player.duration
            if (duration > 0 && duration != androidx.media3.common.C.TIME_UNSET) durationMs = duration
        }

        private fun pause() {
            playingSince?.let { playedMs += clock() - it }
            playingSince = null
        }
    }

    companion object {
        const val BATCH_SIZE = 20
        const val FLUSH_DELAY_MS = 2_000L
        const val TICK_MS = 250L
        const val MIN_PLAYED_MS = 250L

        fun toWire(event: InteractionEvent): InteractionEventWire = when (event) {
            is InteractionEvent.PhotoOpen -> InteractionEventWire(
                typeCode = event.type.wireCode,
                vs = event.vs.orEmpty(),
                attachmentId = event.attachmentId.orEmpty(),
                mediaIdx = event.mediaIdx,
                sourceCode = event.source.sentCode,
            )
            is InteractionEvent.VideoProgress -> InteractionEventWire(
                typeCode = event.type.wireCode,
                vs = event.vs,
                attachmentId = event.attachmentId,
                playedMs = event.playedMs,
                durationMs = event.durationMs,
                sourceCode = event.source.sentCode,
            )
        }
    }
}
