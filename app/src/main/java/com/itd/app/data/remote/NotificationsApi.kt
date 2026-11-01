package com.itd.app.data.remote

import com.itd.app.data.model.*

class NotificationsApi(private val client: ApiClient) {
    suspend fun notifications(offset: Int = 0, limit: Int = 20): NotificationsResponse =
        client.get("/api/notifications/", mapOf("offset" to offset, "limit" to limit))
    suspend fun count(): NotificationCount = client.get("/api/notifications/count")
    suspend fun markAllRead() = client.postEmpty("/api/notifications/read-all")
    suspend fun settings(): NotificationSettings = client.get("/api/notifications/settings")
    suspend fun updateSettings(settings: NotificationSettings): NotificationSettings = client.put("/api/notifications/settings", settings)
}
