package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TopClan(
    val avatar: String? = null,
    val memberCount: Int = 0,
)
