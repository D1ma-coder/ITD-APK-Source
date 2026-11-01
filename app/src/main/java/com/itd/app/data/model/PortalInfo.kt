package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PortalInfo(
    val active: Boolean,
    val url: String,
    val title: String = "Портал",
)
