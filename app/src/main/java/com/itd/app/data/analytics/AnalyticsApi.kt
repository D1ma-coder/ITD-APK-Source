package com.itd.app.data.analytics

import com.itd.app.data.remote.ApiClient
import com.itd.app.util.LogTag
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement

class AnalyticsApi(private val client: ApiClient, private val debugStore: AnalyticsDebugStore) {
    suspend fun sendDwell(batch: DwellBatchWire) =
        send("/api/v1/i", batch.events.size, client.json.encodeToJsonElement(DwellBatchWire.serializer(), batch))

    suspend fun sendInteractions(batch: InteractionBatchWire) =
        send("/api/v1/x", batch.events.size, client.json.encodeToJsonElement(InteractionBatchWire.serializer(), batch))

    private suspend fun send(endpoint: String, count: Int, body: JsonElement) {
        val startedAt = System.currentTimeMillis()
        var status: Int? = null
        var error: String? = null
        LogTag.ANALYTICS.tree().d("send %s events=%d", endpoint, count)
        try {
            status = client.exchange(HttpMethod.Post, endpoint, body).status.value
            if (status in 200..299) LogTag.ANALYTICS.tree().d("%s -> %s", endpoint, status)
            else LogTag.ANALYTICS.tree().w("%s -> %s", endpoint, status)
        } catch (e: Exception) {
            error = "${e.javaClass.simpleName}: ${e.message ?: ""}"
            LogTag.ANALYTICS.tree().w("%s failed: %s", endpoint, error)
        } finally {
            debugStore.recordBatch(BatchSnapshot(endpoint, count, startedAt, System.currentTimeMillis() - startedAt, status, error))
        }
    }
}
