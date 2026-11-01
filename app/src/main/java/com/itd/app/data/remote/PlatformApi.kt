package com.itd.app.data.remote

import com.itd.app.data.model.*

class PlatformApi(private val client: ApiClient) {
    suspend fun announcements(): AnnouncementsResponse = client.get("/api/platform/announcements")
    suspend fun version(): VersionInfoResponse = client.get("/api/platform/version")
    suspend fun portal(): PortalInfo = client.get("/api/v1/portal")
}
