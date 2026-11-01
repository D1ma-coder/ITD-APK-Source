package com.itd.app.data.remote

import com.itd.app.data.model.*
import io.ktor.http.encodeURLPathPart

class SearchApi(private val client: ApiClient) {
    suspend fun search(query: String, userLimit: Int = 20, hashtagLimit: Int = 20): SearchResponse =
        client.get("/api/search/", mapOf("q" to query, "userLimit" to userLimit, "hashtagLimit" to hashtagLimit))
    suspend fun trending(limit: Int = 20): TrendingHashtagsResponse = client.get("/api/hashtags/trending", mapOf("limit" to limit))
    suspend fun hashtagPosts(hashtag: String, limit: Int = 20, cursor: String? = null): HashtagPostsResponse =
        client.get("/api/hashtags/${hashtag.encodeURLPathPart()}/posts", mapOf("limit" to limit, "cursor" to cursor))
    suspend fun topClans(): TopClansResponse = client.get("/api/users/stats/top-clans")
    suspend fun whoToFollow(): WhoToFollowResponse = client.get("/api/users/suggestions/who-to-follow")
}
