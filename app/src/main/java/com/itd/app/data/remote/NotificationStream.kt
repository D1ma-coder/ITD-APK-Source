package com.itd.app.data.remote

import com.itd.app.data.model.NotificationItem
import com.itd.app.util.LogTag
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/** Server-sent notification stream; reconnects five seconds after any disconnect while running. */
class NotificationStream(client: OkHttpClient, private val onNotification: (NotificationItem, Boolean) -> Unit) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
    private var source: EventSource? = null

    @Volatile
    private var running = true

    @Volatile
    private var generation = 0

    fun start() {
        generation++
        running = true
        connect(generation)
    }

    fun stop() {
        generation++
        running = false
        source?.cancel()
        source = null
        LogTag.PUSH.tree().d("notification stream stopped")
    }

    private fun connect(attempt: Int) {
        if (attempt != generation || !running) return
        source?.cancel()
        val request = Request.Builder()
            .url("https://xn--d1ah4a.com/api/notifications/stream")
            .header("Accept", "text/event-stream")
            .build()
        source = EventSources.createFactory(client).newEventSource(request, Listener(attempt))
    }

    private fun reconnect(attempt: Int) {
        if (attempt != generation || !running) return
        LogTag.PUSH.tree().d("notification stream reconnecting")
        Thread {
            Thread.sleep(5_000)
            connect(attempt)
        }.start()
    }

    private inner class Listener(private val attempt: Int) : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            LogTag.PUSH.tree().d("notification stream open")
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            when (type) {
                "connected" -> LogTag.PUSH.tree().d("notification stream connected")
                "notification" -> try {
                    val item = json.decodeFromString(NotificationItem.serializer(), data)
                    LogTag.PUSH.tree().d("stream notification id=%s", item.id)
                    onNotification(item, item.sound)
                } catch (e: Exception) {
                    LogTag.PUSH.tree().e(e, "bad notification event")
                }
                else -> LogTag.PUSH.tree().d("stream event %s", type)
            }
        }

        override fun onClosed(eventSource: EventSource) {
            LogTag.PUSH.tree().d("notification stream closed")
            reconnect(attempt)
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            LogTag.PUSH.tree().e(t, "notification stream failed")
            response?.close()
            reconnect(attempt)
        }
    }
}
