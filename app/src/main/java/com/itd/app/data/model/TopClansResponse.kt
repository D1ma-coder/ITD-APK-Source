package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TopClansResponse(
    val clans: List<TopClan>,
)
