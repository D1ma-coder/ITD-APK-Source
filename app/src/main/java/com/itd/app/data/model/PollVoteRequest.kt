package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PollVoteRequest(
    val optionIds: List<String>,
)
