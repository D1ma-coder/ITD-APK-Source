package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TrendingHashtag(
    val id: String,
    val name: String,
    val postsCount: Int = 0,
)
