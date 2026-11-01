package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FeedPagination(
    val limit: Int,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)
