package com.itd.app.data.remote

import com.itd.app.util.LogTag
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess

class DevicesApi(private val client: ApiClient) {
    suspend fun register(token: String): Boolean = try {
        val body = client.json.encodeToJsonElement(RegisterDeviceRequest.serializer(), RegisterDeviceRequest(token))
        val response = client.exchange(HttpMethod.Post, "/api/devices", body)
        LogTag.APP.tree().d("register device status=%d", response.status.value)
        response.status.isSuccess()
    } catch (e: Exception) {
        LogTag.APP.tree().e(e, "register device failed")
        false
    }

    suspend fun unregister(token: String): Boolean = try {
        val response = client.exchange(HttpMethod.Delete, "/api/devices/$token")
        LogTag.APP.tree().d("unregister device status=%d", response.status.value)
        response.status.isSuccess()
    } catch (e: Exception) {
        LogTag.APP.tree().e(e, "unregister device failed")
        false
    }
}
