package com.itd.app.decompose

import kotlinx.serialization.Serializable

/** Full-screen overlays shown above the tabs. */
@Serializable
sealed class OverlayConfig {
    @Serializable
    data object None : OverlayConfig()

    @Serializable
    data class PostDetail(
        val postJson: String,
        val targetCommentId: String? = null,
        val id: Long = System.nanoTime(),
    ) : OverlayConfig()

    @Serializable
    data class Profile(
        val username: String,
        val isOwnProfile: Boolean = false,
        val id: Long = System.nanoTime(),
    ) : OverlayConfig()

    @Serializable
    data class Portal(
        val url: String,
        val title: String = "Портал",
        val id: Long = System.nanoTime(),
    ) : OverlayConfig()

    @Serializable
    data class BannerPainter(val id: Long = System.nanoTime()) : OverlayConfig()
}
