package com.itd.app.data.repository

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.itd.app.data.model.PostStats
import com.itd.app.data.remote.PostsApi
import com.itd.app.util.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Refreshes counters of posts that stay on screen: a post joins after 250 ms of visibility,
 * leaves 1 s after it disappears, and visible posts are polled every 3 s (20 per request)
 * until the user has been idle for 30 s.
 */
class PostStatsPoller(private val api: PostsApi, private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val watched = HashSet<String>()
    private val joining = HashMap<String, Runnable>()
    private val leaving = HashMap<String, Runnable>()
    private var lastActivityAt = clock()
    private var idle = false
    private var backgrounded = false
    private var inFlight = false
    private var nextFetchAt = 0L
    private val fetch = Runnable { fetchVisible() }

    fun onVisible(postId: String) {
        synchronized(lock) {
            leaving.remove(postId)?.let(handler::removeCallbacks)
            if (postId in watched || joining.containsKey(postId)) return
            val runnable = Runnable {
                synchronized(lock) {
                    joining.remove(postId)
                    if (watched.add(postId)) schedule(immediate = false)
                }
            }
            joining[postId] = runnable
            handler.postDelayed(runnable, JOIN_DELAY_MS)
        }
    }

    fun onHidden(postId: String) {
        synchronized(lock) {
            joining.remove(postId)?.let(handler::removeCallbacks)
            if (postId !in watched || leaving.containsKey(postId)) return
            val runnable = Runnable {
                synchronized(lock) {
                    leaving.remove(postId)
                    watched.remove(postId)
                    if (watched.isEmpty()) cancelFetch()
                }
            }
            leaving[postId] = runnable
            handler.postDelayed(runnable, LEAVE_DELAY_MS)
        }
    }

    fun remove(postId: String) {
        synchronized(lock) {
            joining.remove(postId)?.let(handler::removeCallbacks)
            leaving.remove(postId)?.let(handler::removeCallbacks)
            watched.remove(postId)
            if (watched.isEmpty()) cancelFetch()
        }
    }

    fun onUserActivity() {
        lastActivityAt = clock()
        if (idle) {
            idle = false
            schedule(immediate = true)
        }
    }

    internal fun onForeground() {
        backgrounded = false
        lastActivityAt = clock()
        schedule(immediate = true)
    }

    internal fun onBackground() {
        backgrounded = true
        handler.removeCallbacks(fetch)
        nextFetchAt = 0L
    }

    private fun schedule(immediate: Boolean) {
        if (backgrounded || idle) return
        synchronized(lock) {
            val delay = if (immediate) 0L else SETTLE_DELAY_MS
            val at = clock() + delay
            if (!immediate && nextFetchAt != 0L && at >= nextFetchAt) return
            handler.removeCallbacks(fetch)
            nextFetchAt = at
            handler.postDelayed(fetch, delay)
        }
    }

    private fun scheduleNext() {
        if (backgrounded || idle) return
        synchronized(lock) {
            if (watched.isEmpty()) {
                nextFetchAt = 0L
            } else {
                nextFetchAt = clock() + POLL_INTERVAL_MS
                handler.postDelayed(fetch, POLL_INTERVAL_MS)
            }
        }
    }

    private fun cancelFetch() {
        handler.removeCallbacks(fetch)
        nextFetchAt = 0L
    }

    private fun fetchVisible() {
        if (inFlight || backgrounded) return
        if (clock() - lastActivityAt > IDLE_MS) {
            idle = true
            cancelFetch()
            return
        }
        val ids = synchronized(lock) {
            if (watched.isEmpty()) {
                nextFetchAt = 0L
                return
            }
            inFlight = true
            watched.take(MAX_BATCH)
        }
        scope.launch {
            try {
                publish(api.stats(ids).posts)
            } catch (_: Exception) {
                LogTag.ANALYTICS.tree().w("post stats refresh failed")
            } finally {
                inFlight = false
                scheduleNext()
            }
        }
    }

    private fun publish(stats: List<PostStats>) {
        stats.forEach {
            PostUpdates.emit(
                PostUpdate(
                    postId = it.id,
                    likesCount = it.likesCount,
                    repostsCount = it.repostsCount,
                    commentsCount = it.commentsCount,
                    viewsCount = it.viewsCount,
                    dominantEmoji = it.dominantEmoji,
                    dominantEmojiCleared = it.dominantEmoji == null,
                ),
            )
        }
    }

    companion object {
        const val JOIN_DELAY_MS = 250L
        const val LEAVE_DELAY_MS = 1_000L
        const val SETTLE_DELAY_MS = 500L
        const val POLL_INTERVAL_MS = 3_000L
        const val IDLE_MS = 30_000L
        const val MAX_BATCH = 20

        @Volatile
        private var instance: PostStatsPoller? = null

        fun get(api: PostsApi): PostStatsPoller = instance ?: synchronized(this) {
            instance ?: PostStatsPoller(api).also { poller ->
                instance = poller
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) = poller.onForeground()
                    override fun onStop(owner: LifecycleOwner) = poller.onBackground()
                })
            }
        }
    }
}
