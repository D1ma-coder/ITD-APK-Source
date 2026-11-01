package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FileUploadResponse(
    val id: String,
    val url: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
)
