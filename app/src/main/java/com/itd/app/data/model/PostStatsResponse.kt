package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PostStatsResponse(
    val posts: List<PostStats> = emptyList(),
)
