package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionsResponse(
    val sessions: List<Session>,
)
