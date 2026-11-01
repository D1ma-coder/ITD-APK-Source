package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RepliesData(
    val replies: List<Comment>,
    val pagination: RepliesPagination,
)
