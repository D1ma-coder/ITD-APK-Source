package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PollOption(
    val id: String,
    val text: String,
    val position: Int = 0,
    val votesCount: Int = 0,
)
