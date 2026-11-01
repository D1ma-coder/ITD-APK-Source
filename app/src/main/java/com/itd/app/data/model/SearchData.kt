package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchData(
    val users: List<SuggestedUser> = emptyList(),
    val hashtags: List<TrendingHashtag> = emptyList(),
)
