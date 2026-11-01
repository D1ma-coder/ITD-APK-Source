package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementImage(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)
