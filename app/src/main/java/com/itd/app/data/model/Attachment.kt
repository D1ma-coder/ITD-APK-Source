package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Attachment(
    val id: String,
    val type: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Int? = null,
    val order: Int = 0,
)
