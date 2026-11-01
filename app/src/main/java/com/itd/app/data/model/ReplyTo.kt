package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ReplyTo(
    val id: String,
    val username: String?,
    val displayName: String,
)
