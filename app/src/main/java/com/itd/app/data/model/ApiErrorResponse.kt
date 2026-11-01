package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    val error: ApiErrorDetail,
)
