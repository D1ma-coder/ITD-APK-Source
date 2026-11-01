package com.itd.app.decompose

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.itd.app.data.model.Post
import kotlinx.serialization.json.Json

sealed class OverlayChild {
    data object None : OverlayChild()
    data class PostDetail(val post: Post, val targetCommentId: String?) : OverlayChild()
    data class Profile(val username: String, val isOwnProfile: Boolean) : OverlayChild()
    data class Portal(val url: String, val title: String) : OverlayChild()
    data object BannerPainter : OverlayChild()
}

/** Stack of overlays (post, profile, portal, banner painter) above the tab content. */
class OverlayComponent(
    componentContext: ComponentContext,
    private val ownUsername: String?,
    private val json: Json,
) : ComponentContext by componentContext {
    private val navigation = StackNavigation<OverlayConfig>()

    val stack: Value<ChildStack<OverlayConfig, OverlayChild>> = childStack(
        source = navigation,
        serializer = OverlayConfig.serializer(),
        initialConfiguration = OverlayConfig.None,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    private fun createChild(config: OverlayConfig, context: ComponentContext): OverlayChild = when (config) {
        OverlayConfig.None -> OverlayChild.None
        is OverlayConfig.PostDetail -> OverlayChild.PostDetail(json.decodeFromString(Post.serializer(), config.postJson), config.targetCommentId)
        is OverlayConfig.Profile -> OverlayChild.Profile(config.username, config.isOwnProfile)
        is OverlayConfig.Portal -> OverlayChild.Portal(config.url, config.title)
        is OverlayConfig.BannerPainter -> OverlayChild.BannerPainter
    }

    private val top: OverlayConfig
        get() = stack.value.active.configuration

    fun pop() {
        if (stack.value.backStack.isEmpty()) return
        navigation.pop()
    }

    fun openPortal(url: String, title: String) {
        if (top is OverlayConfig.Portal) return
        navigation.pushNew(OverlayConfig.Portal(url, title))
    }

    fun openPost(post: Post, targetCommentId: String? = null) {
        navigation.pushNew(OverlayConfig.PostDetail(json.encodeToString(Post.serializer(), post), targetCommentId))
    }

    fun openProfile(username: String) {
        if (username == ownUsername.orEmpty()) return
        val current = top
        if (current is OverlayConfig.Profile && current.username == username) return
        navigation.pushNew(OverlayConfig.Profile(username))
    }

    fun openBannerPainter() {
        if (top is OverlayConfig.BannerPainter) return
        navigation.pushNew(OverlayConfig.BannerPainter())
    }
}
