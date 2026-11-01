package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VerifyOtpRequest(
    val email: String,
    val password: String,
    val otp: String,
    val flowToken: String,
)
