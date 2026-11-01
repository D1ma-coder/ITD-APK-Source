package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserPin(
    val slug: String,
    val name: String,
    val description: String?,
    val url: String? = null,
    val grantedAt: String? = null,
)
