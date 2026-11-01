package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RepliesPagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    val hasMore: Boolean = false,
)
