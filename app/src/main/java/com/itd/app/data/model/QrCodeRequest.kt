package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class QrCodeRequest(
    val qrId: String,
    val secret: String,
)
