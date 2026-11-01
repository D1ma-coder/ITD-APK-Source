package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WhoToFollowResponse(
    val users: List<SuggestedUser>,
)
