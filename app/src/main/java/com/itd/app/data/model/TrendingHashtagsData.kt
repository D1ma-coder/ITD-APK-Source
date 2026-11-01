package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TrendingHashtagsData(
    val hashtags: List<TrendingHashtag>,
)
