package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Post(
    val id: String,
    val content: String? = null,
    val author: User,
    val attachments: List<Attachment> = emptyList(),
    val createdAt: String,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val repostsCount: Int = 0,
    val viewsCount: Int = 0,
    val isLiked: Boolean = false,
    val isReposted: Boolean = false,
    val isOwner: Boolean = false,
    val isViewed: Boolean = false,
    val authorId: String? = null,
    val wallRecipientId: String? = null,
    val originalPost: Post? = null,
    val isDeleted: Boolean = false,
    val isPinned: Boolean = false,
    val editedAt: String? = null,
    val spans: List<PostSpan> = emptyList(),
    val poll: PostPoll? = null,
    val vs: String? = null,
    val dominantEmoji: String? = null,
)
