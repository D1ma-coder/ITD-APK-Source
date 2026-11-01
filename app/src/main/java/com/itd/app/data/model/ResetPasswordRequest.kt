package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val flowToken: String,
    val otp: String,
    val newPassword: String,
)
