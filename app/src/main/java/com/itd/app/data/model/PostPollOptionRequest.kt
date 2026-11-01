package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PostPollOptionRequest(
    val text: String,
)
