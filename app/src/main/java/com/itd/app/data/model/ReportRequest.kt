package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ReportRequest(
    val targetType: String,
    val targetId: String,
    val reason: String,
    val description: String? = null,
)
