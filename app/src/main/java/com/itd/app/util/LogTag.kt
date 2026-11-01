package com.itd.app.util

import timber.log.Timber

enum class LogTag(val tag: String) {
    APP("App"),
    AUTH("Auth"),
    NETWORK("Net"),
    AUDIO("Audio"),
    VIDEO("Video"),
    CAMERA("Camera"),
    MEDIA("Media"),
    FEED("Feed"),
    PROFILE("Profile"),
    SEARCH("Search"),
    PUSH("Push"),
    ANALYTICS("Analytics"),
    STORAGE("Storage"),
    UI("UI");

    fun tree(): Timber.Tree = Timber.tag(tag)
}
