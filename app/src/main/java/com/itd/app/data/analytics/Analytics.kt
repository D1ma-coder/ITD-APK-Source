package com.itd.app.data.analytics

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.itd.app.data.remote.ApiClient
import java.util.UUID

/** Process-wide analytics entry point: one session id, one dwell and one interaction tracker. */
class Analytics(val dwell: DwellTracker, val interactions: InteractionTracker, val debugStore: AnalyticsDebugStore) {
    companion object {
        val sessionId: String by lazy { UUID.randomUUID().toString() }

        @Volatile
        private var instance: Analytics? = null
        private var observing = false

        fun get(client: ApiClient): Analytics {
            val analytics = instance ?: synchronized(this) {
                instance ?: run {
                    val store = AnalyticsDebugStore()
                    val api = AnalyticsApi(client, store)
                    Analytics(DwellTracker(api, store, sessionId), InteractionTracker(api, store, sessionId), store).also { instance = it }
                }
            }
            if (!observing) {
                observing = true
                ProcessLifecycleOwner.get().lifecycle.addObserver(AnalyticsLifecycleObserver(analytics))
            }
            return analytics
        }
    }
}

private class AnalyticsLifecycleObserver(private val analytics: Analytics) : DefaultLifecycleObserver {
    override fun onPause(owner: LifecycleOwner) {
        analytics.dwell.stopAll(HiddenReason.BACKGROUNDED, sync = false)
        analytics.interactions.stopAll(sync = false)
    }

    override fun onStop(owner: LifecycleOwner) {
        analytics.dwell.stopAll(HiddenReason.PUSH_VIEW_HIDDEN, sync = true)
        analytics.interactions.stopAll(sync = true)
    }
}
