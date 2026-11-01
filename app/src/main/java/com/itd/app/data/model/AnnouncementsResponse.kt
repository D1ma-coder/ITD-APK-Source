package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementsResponse(
    val announcements: List<Announcement> = emptyList(),
)
