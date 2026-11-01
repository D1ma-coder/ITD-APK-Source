package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PostPollRequest(
    val question: String,
    val options: List<PostPollOptionRequest>,
    val multipleChoice: Boolean = false,
)
