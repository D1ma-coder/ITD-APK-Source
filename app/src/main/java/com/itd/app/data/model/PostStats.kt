package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PostStats(
    val id: String,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val repostsCount: Int = 0,
    val viewsCount: Int = 0,
    val dominantEmoji: String? = null,
)
