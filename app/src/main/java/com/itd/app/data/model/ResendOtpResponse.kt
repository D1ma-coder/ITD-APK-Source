package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ResendOtpResponse(
    val message: String,
    val email: String,
)
