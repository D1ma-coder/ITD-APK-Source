package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LikeResponse(
    val liked: Boolean,
    val likesCount: Int,
)
