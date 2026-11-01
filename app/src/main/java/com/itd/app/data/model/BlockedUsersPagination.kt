package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BlockedUsersPagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    val hasMore: Boolean,
)
