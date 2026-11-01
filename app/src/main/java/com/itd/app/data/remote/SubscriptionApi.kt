package com.itd.app.data.remote

import com.itd.app.data.model.*
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart

class SubscriptionApi(private val client: ApiClient) {
    suspend fun bindCard(): BindCardResponse = client.post("/api/v1/subscription/bind-card")
    suspend fun paymentMethods(): PaymentMethodsResponse = client.get("/api/v1/subscription/methods")
    suspend fun deleteMethod(id: String): DeleteMethodResponse = client.request(HttpMethod.Delete, "/api/v1/subscription/methods/${id.encodeURLPathPart()}", null, emptyMap(), DeleteMethodResponse.serializer())
    suspend fun setDefault(id: String): SetDefaultResponse = client.request(HttpMethod.Put, "/api/v1/subscription/methods/${id.encodeURLPathPart()}/default", null, emptyMap(), SetDefaultResponse.serializer())
    suspend fun setAutoRenewal(enabled: Boolean): AutoRenewalResponse = client.post("/api/v1/subscription/auto-renewal", AutoRenewalRequest(enabled))
}
