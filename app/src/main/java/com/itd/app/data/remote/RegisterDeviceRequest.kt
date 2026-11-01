package com.itd.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(val token: String)
