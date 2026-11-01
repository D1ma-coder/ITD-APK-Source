package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val id: String,
    val content: String,
    val author: User,
    val likesCount: Int = 0,
    val repliesCount: Int = 0,
    val isLiked: Boolean = false,
    val createdAt: String,
    val attachments: List<Attachment> = emptyList(),
    val replyTo: ReplyTo? = null,
    val replies: List<Comment> = emptyList(),
)
