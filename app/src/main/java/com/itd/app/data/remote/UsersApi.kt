package com.itd.app.data.remote

import com.itd.app.data.model.*
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart

class UsersApi(private val client: ApiClient) {
    suspend fun profile(username: String): UserProfile = client.get("/api/users/${username.encodeURLPathPart()}")
    suspend fun currentAccount(): AccountStatusResponse = client.get("/api/users/me")
    suspend fun checkUsername(username: String): UsernameCheckResponse = client.get("/api/users/check-username", mapOf("username" to username))
    suspend fun createProfile(request: CreateProfileRequest): CreateProfileResponse = client.post("/api/users/profile", request)
    suspend fun updateProfile(request: UpdateProfileRequest): UpdateProfileResponse = client.put("/api/users/me", request)
    suspend fun follow(id: String) = client.postEmpty("/api/users/${id.encodeURLPathPart()}/follow")
    suspend fun unfollow(id: String) = client.delete("/api/users/${id.encodeURLPathPart()}/follow")
    suspend fun block(id: String) = client.postEmpty("/api/users/${id.encodeURLPathPart()}/block")
    suspend fun unblock(id: String) = client.delete("/api/users/${id.encodeURLPathPart()}/block")
    suspend fun blocked(page: Int = 1, limit: Int = 20): BlockedUsersResponse = client.get("/api/users/me/blocked", mapOf("page" to page, "limit" to limit))
    suspend fun followers(id: String, page: Int = 1, limit: Int = 20): FollowListResponse = client.get("/api/users/${id.encodeURLPathPart()}/followers", mapOf("page" to page, "limit" to limit))
    suspend fun following(id: String, page: Int = 1, limit: Int = 20): FollowListResponse = client.get("/api/users/${id.encodeURLPathPart()}/following", mapOf("page" to page, "limit" to limit))
    suspend fun posts(id: String, limit: Int = 20, cursor: String? = null, pinnedPostId: String? = null): FeedResponse =
        client.get("/api/posts/user/${id.encodeURLPathPart()}", mapOf("limit" to limit, "sort" to "new", "cursor" to cursor, "pinnedPostId" to pinnedPostId))
    suspend fun likedPosts(id: String, limit: Int = 20, cursor: String? = null): FeedResponse =
        client.get("/api/posts/user/${id.encodeURLPathPart()}/liked", mapOf("limit" to limit, "cursor" to cursor))
    suspend fun pins(): UserPinsResponse = client.get("/api/users/me/pins")
    suspend fun setPin(id: String) { client.send(HttpMethod.Put, "/api/users/me/pin", client.json.encodeToJsonElement(SetPinRequest.serializer(), SetPinRequest(id))) }
    suspend fun removePin() = client.delete("/api/users/me/pin")
    suspend fun privacy(): PrivacySettings = client.get("/api/users/me/privacy")
    suspend fun updatePrivacy(request: UpdatePrivacyRequest): PrivacySettings = client.put("/api/users/me/privacy", request)
    suspend fun deleteAccount(): DeleteAccountResponse = client.request(HttpMethod.Delete, "/api/users/me", null, emptyMap(), DeleteAccountResponse.serializer())
    suspend fun restoreAccount(): RestoreAccountResponse = client.post("/api/users/me/restore")
}
