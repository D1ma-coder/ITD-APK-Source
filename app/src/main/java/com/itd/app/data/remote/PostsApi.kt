package com.itd.app.data.remote

import com.itd.app.data.model.*
import com.itd.app.ui.screens.bannerpainter.BannerDrawing
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLPathPart

class PostsApi(private val client: ApiClient) {
    suspend fun feed(tab: String = "popular", limit: Int = 20, cursor: String? = null): FeedResponse =
        client.get("/api/posts", mapOf("tab" to tab, "limit" to limit, "cursor" to cursor))

    suspend fun post(id: String): SinglePostResponse = client.get("/api/posts/${id.encodeURLPathPart()}")
    suspend fun create(request: CreatePostRequest): Post = client.post("/api/posts", request)
    suspend fun edit(id: String, request: EditPostRequest) {
        client.send(HttpMethod.Put, "/api/posts/${id.encodeURLPathPart()}", client.json.encodeToJsonElement(EditPostRequest.serializer(), request))
    }
    suspend fun delete(id: String) = client.delete("/api/posts/${id.encodeURLPathPart()}")
    suspend fun stats(ids: List<String>): PostStatsResponse = client.post("/api/posts/stats", PostStatsRequest(ids))
    suspend fun like(id: String): LikeResponse = client.post("/api/posts/${id.encodeURLPathPart()}/like")
    suspend fun unlike(id: String) = client.delete("/api/posts/${id.encodeURLPathPart()}/like")
    suspend fun pin(id: String): PinResponse = client.post("/api/posts/${id.encodeURLPathPart()}/pin")
    suspend fun unpin(id: String): PinResponse = client.request(HttpMethod.Delete, "/api/posts/${id.encodeURLPathPart()}/pin", null, emptyMap(), PinResponse.serializer())
    suspend fun repost(id: String, content: String? = null): Post = client.post("/api/posts/${id.encodeURLPathPart()}/repost", RepostRequest(content))
    suspend fun report(request: ReportRequest) {
        client.send(HttpMethod.Post, "/api/reports", client.json.encodeToJsonElement(ReportRequest.serializer(), request))
    }
    suspend fun vote(id: String, optionIds: List<String>): PollVoteResponse = client.post("/api/posts/${id.encodeURLPathPart()}/poll/vote", PollVoteRequest(optionIds))
    suspend fun comments(postId: String, limit: Int = 20, sort: String = "popular", cursor: String? = null): CommentsResponse =
        client.get("/api/posts/${postId.encodeURLPathPart()}/comments", mapOf("limit" to limit, "sort" to sort, "cursor" to cursor))
    suspend fun comment(postId: String, content: String, attachmentIds: List<String> = emptyList()): Comment =
        client.post("/api/posts/${postId.encodeURLPathPart()}/comments", CreateCommentRequest(content, attachmentIds))
    suspend fun replies(commentId: String, page: Int = 1, limit: Int = 20): RepliesResponse =
        client.get("/api/comments/${commentId.encodeURLPathPart()}/replies", mapOf("page" to page, "limit" to limit, "sort" to "oldest"))
    suspend fun reply(commentId: String, content: String, replyToUserId: String, attachmentIds: List<String> = emptyList()): Comment =
        client.post("/api/comments/${commentId.encodeURLPathPart()}/replies", CreateReplyRequest(content, replyToUserId, attachmentIds))
    suspend fun uploadBannerDrawing(drawing: BannerDrawing): FileUploadResponse {
        val body = drawing.toJson()
        if (body.length > BannerDrawing.MAX_JSON_BYTES) throw ApiException("BANNER_DRAWING_TOO_LARGE", "Banner drawing is too large", 413)
        val response = client.exchange(HttpMethod.Post, "/api/files/banner-drawing", TextContent(body, ContentType.Application.Json))
        if (response.status == HttpStatusCode.TooManyRequests) {
            throw ApiException(
                "BANNER_DRAWING_BUSY",
                "Banner drawing service is busy",
                response.status.value,
                response.headers[HttpHeaders.RetryAfter]?.trim()?.toIntOrNull(),
            )
        }
        if (!response.status.isSuccess()) throw client.apiError(response)
        return client.json.decodeFromString(FileUploadResponse.serializer(), response.bodyAsText())
    }

    suspend fun deleteComment(id: String) = client.delete("/api/comments/${id.encodeURLPathPart()}")
    suspend fun likeComment(id: String): LikeResponse = client.post("/api/comments/${id.encodeURLPathPart()}/like")
    suspend fun unlikeComment(id: String) = client.delete("/api/comments/${id.encodeURLPathPart()}/like")
}
