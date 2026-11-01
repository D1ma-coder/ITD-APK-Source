package com.itd.app.data.repository

import com.itd.app.data.model.Post
import com.itd.app.data.model.PostPoll
import com.itd.app.data.model.PostSpan
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A partial change to a post that every screen showing it should apply. */
data class PostUpdate(
    val postId: String,
    val isLiked: Boolean? = null,
    val likesCount: Int? = null,
    val isReposted: Boolean? = null,
    val repostsCount: Int? = null,
    val commentsCount: Int? = null,
    val viewsCount: Int? = null,
    val dominantEmoji: String? = null,
    val dominantEmojiCleared: Boolean = false,
    val poll: PostPoll? = null,
    val content: String? = null,
    val spans: List<PostSpan>? = null,
    val editedAt: String? = null,
)

object PostUpdates {
    private val _updates = MutableSharedFlow<PostUpdate>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val updates: SharedFlow<PostUpdate> = _updates.asSharedFlow()

    fun emit(update: PostUpdate) {
        _updates.tryEmit(update)
    }

    private val _removed = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Ids of posts deleted by the user; lists drop them. */
    val removed: SharedFlow<String> = _removed.asSharedFlow()

    fun remove(postId: String) {
        _removed.tryEmit(postId)
    }

    private val _created = MutableSharedFlow<com.itd.app.data.model.Post>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Posts (including reposts) created by the user; feeds prepend them. */
    val created: SharedFlow<com.itd.app.data.model.Post> = _created.asSharedFlow()

    fun create(post: com.itd.app.data.model.Post) {
        _created.tryEmit(post)
    }

    fun apply(post: Post, update: PostUpdate): Post {
        if (post.id != update.postId) {
            val original = post.originalPost ?: return post
            return if (original.id == update.postId) post.copy(originalPost = apply(original, update)) else post
        }
        return post.copy(
            isLiked = update.isLiked ?: post.isLiked,
            likesCount = update.likesCount ?: post.likesCount,
            isReposted = update.isReposted ?: post.isReposted,
            repostsCount = update.repostsCount ?: post.repostsCount,
            commentsCount = update.commentsCount ?: post.commentsCount,
            viewsCount = update.viewsCount ?: post.viewsCount,
            dominantEmoji = if (update.dominantEmojiCleared) null else update.dominantEmoji ?: post.dominantEmoji,
            poll = update.poll ?: post.poll,
            content = update.content ?: post.content,
            spans = update.spans ?: post.spans,
            editedAt = update.editedAt ?: post.editedAt,
        )
    }
}
