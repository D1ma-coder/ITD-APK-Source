package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementButton(
    val title: String,
    val style: String? = null,
    val action: AnnouncementAction? = null,
)
