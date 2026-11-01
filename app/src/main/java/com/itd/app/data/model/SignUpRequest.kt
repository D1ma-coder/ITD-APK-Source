package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val token: String? = null,
    val turnstileToken: String? = null,
)
