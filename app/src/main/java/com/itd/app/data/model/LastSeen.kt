package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LastSeen(
    val unit: String? = null,
    val value: Int? = null,
)
