package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class HashtagPostsResponse(
    val data: HashtagPostsData,
)
