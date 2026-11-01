package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FeedResponse(
    val data: FeedData,
)
