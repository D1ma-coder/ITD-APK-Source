package com.itd.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Announcement(
    val id: String,
    val image: AnnouncementImage? = null,
    val badge: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("additional_text")
    val additionalText: String? = null,
    val buttons: List<AnnouncementButton> = emptyList(),
)
