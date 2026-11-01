package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FeedData(
    val posts: List<Post>,
    val pagination: FeedPagination,
)
