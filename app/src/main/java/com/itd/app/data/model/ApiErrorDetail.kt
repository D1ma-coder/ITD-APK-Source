package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorDetail(
    val code: String,
    val message: String,
    val retryAfter: Int? = null,
    val attemptsLeft: Int? = null,
)
