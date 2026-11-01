package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementAction(
    val type: String,
    val url: String? = null,
)
