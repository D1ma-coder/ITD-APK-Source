package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RepostRequest(
    val content: String? = null,
)
