package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserPinsData(
    val pins: List<UserPin>,
    val activePin: String? = null,
)
