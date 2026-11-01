package com.itd.app.data.local

interface SessionStorage {
    var accessToken: String?
    var refreshToken: String?
    val deviceId: String
    fun clear()
}
