package com.itd.app.data.analytics

import android.os.Handler
import android.os.Looper
import com.itd.app.util.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Measures how long each post stays on screen and sends batches to /api/v1/i. */
class DwellTracker(
    private val api: AnalyticsApi,
    private val debugStore: AnalyticsDebugStore,
    private val sessionId: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val active = HashMap<String, DwellTimer>()
    private val seen = HashSet<String>()
    private val pending = ArrayList<DwellEvent>(BATCH_SIZE)
    private var flushScheduled = false
    private val flushRunnable = Runnable { flush() }
    private var lastActivityAt = clock()
    private val _activeDwells = MutableStateFlow<List<ActiveDwell>>(emptyList())
    val activeDwells: StateFlow<List<ActiveDwell>> = _activeDwells

    private val inactivityCheck = object : Runnable {
        override fun run() {
            if (clock() - lastActivityAt >= INACTIVITY_MS) {
                val empty = synchronized(lock) { active.isEmpty() }
                if (!empty) stopAll(HiddenReason.USER_INACTIVE, sync = false)
            }
            handler.postDelayed(this, INACTIVITY_CHECK_MS)
        }
    }

    init {
        handler.postDelayed(inactivityCheck, INACTIVITY_CHECK_MS)
    }

    fun onUserActivity() {
        lastActivityAt = clock()
    }

    fun start(postId: String, vs: String?, source: ImpressionSource, sourceContext: String) {
        if (vs.isNullOrEmpty()) {
            LogTag.ANALYTICS.tree().w("skip dwell without vs post=%s", postId.take(8))
            return
        }
        synchronized(lock) {
            if (active.containsKey(postId)) return
            active[postId] = DwellTimer(vs, source, sourceContext, clock())
            publishActive()
            LogTag.ANALYTICS.tree().d("dwell start post=%s", postId.take(8))
        }
    }

    fun stop(postId: String, reason: HiddenReason) {
        var event: DwellEvent? = null
        val skipReason: String?
        synchronized(lock) {
            val timer = active.remove(postId)
            if (timer == null) {
                skipReason = "no active timer"
            } else {
                val now = clock()
                val duration = (now - timer.enteredAt).toInt()
                publishActive()
                skipReason = when {
                    duration < MIN_DWELL_MS -> "duration<250ms (was ${duration}ms)"
                    duration > MAX_DWELL_MS -> "duration>MAX_DWELL_MS"
                    else -> {
                        event = DwellEvent(postId, duration, duration, timer.enteredAt, now, reason, timer.source, timer.sourceContext, timer.vs, !seen.add(postId))
                        null
                    }
                }
            }
        }
        event?.let {
            LogTag.ANALYTICS.tree().d("dwell stop post=%s", postId.take(8))
            enqueue(it)
            return
        }
        if (skipReason != null) LogTag.ANALYTICS.tree().d("dwell skip post=%s: %s", postId.take(8), skipReason)
    }

    fun stopAll(reason: HiddenReason, sync: Boolean) {
        val timers: HashMap<String, DwellTimer>
        synchronized(lock) {
            timers = HashMap(active)
            active.clear()
            publishActive()
        }
        val now = clock()
        for ((postId, timer) in timers) {
            val duration = (now - timer.enteredAt).toInt()
            if (duration in MIN_DWELL_MS..MAX_DWELL_MS) {
                val repeated = synchronized(lock) { !seen.add(postId) }
                enqueue(DwellEvent(postId, duration, duration, timer.enteredAt, now, reason, timer.source, timer.sourceContext, timer.vs, repeated))
            }
        }
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
            LogTag.ANALYTICS.tree().w("sync dwell flush failed")
        }
    }

    private fun enqueue(event: DwellEvent) {
        val full = synchronized(lock) {
            pending.add(event)
            pending.size >= BATCH_SIZE
        }
        debugStore.recordDwell(DwellLogEntry(event.postId, event.source, event.maxDwellMs, event.hiddenReason, event.subImpression, event.exitTsMs))
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

    private suspend fun send(events: List<DwellEvent>) {
        try {
            if (events.isNotEmpty()) api.sendDwell(DwellBatchWire(sessionId, events.map(::toWire)))
        } catch (_: Exception) {
            LogTag.ANALYTICS.tree().w("dwell batch failed")
        }
    }

    private fun publishActive() {
        _activeDwells.value = active.map { (postId, timer) -> ActiveDwell(postId, timer.source, timer.sourceContext, timer.enteredAt) }
    }

    companion object {
        const val BATCH_SIZE = 20
        const val FLUSH_DELAY_MS = 2_000L
        const val MIN_DWELL_MS = 250
        const val MAX_DWELL_MS = 86_400_000
        const val INACTIVITY_MS = 30_000L
        const val INACTIVITY_CHECK_MS = 5_000L

        fun toWire(event: DwellEvent) = DwellEventWire(
            maxDwellMs = event.maxDwellMs,
            enterTsMs = event.enterTsMs,
            exitTsMs = event.exitTsMs,
            reasonCode = event.hiddenReason.code,
            vs = event.vs,
            sourceContext = event.sourceContext.ifEmpty { null },
            sourceCode = event.source.sentCode,
            subImpression = if (event.subImpression) 1 else null,
        )
    }
}
