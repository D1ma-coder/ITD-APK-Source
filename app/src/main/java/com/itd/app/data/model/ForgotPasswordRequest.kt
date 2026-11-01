package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ForgotPasswordRequest(
    val email: String,
    val token: String? = null,
    val turnstileToken: String? = null,
)
