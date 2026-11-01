package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CommentsData(
    val comments: List<Comment>,
    val total: Int,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
)
