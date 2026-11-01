package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PostStatsRequest(
    val ids: List<String>,
)
