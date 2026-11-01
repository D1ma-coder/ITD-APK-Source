package com.itd.app.ui.navigation

import com.itd.app.ui.screens.hashtag.HashtagScreen
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.itd.app.data.model.NotificationItem
import com.itd.app.data.model.Post
import com.itd.app.data.model.User
import com.itd.app.data.remote.NotificationStream
import com.itd.app.data.remote.ShownNotifications
import com.itd.app.data.remote.showNotification
import com.itd.app.data.repository.PostStatsPoller
import com.itd.app.decompose.OverlayChild
import com.itd.app.decompose.OverlayComponent
import com.itd.app.di.AppContainer
import com.itd.app.di.viewModelFactory
import com.itd.app.halloween.HalloweenArt
import com.itd.app.halloween.HalloweenFeed
import com.itd.app.halloween.HalloweenNavigation
import com.itd.app.halloween.HalloweenNotices
import com.itd.app.halloween.HalloweenNotificationSounds
import com.itd.app.halloween.HalloweenPortalManager
import com.itd.app.halloween.HalloweenSounds
import com.itd.app.halloween.HalloweenToastHost
import com.itd.app.ui.components.ItdWebPage
import com.itd.app.ui.screens.bannerpainter.BannerPainterScreen
import com.itd.app.ui.screens.createpost.CreatePostScreen
import com.itd.app.ui.screens.createpost.CreatePostViewModel
import com.itd.app.ui.screens.feed.FeedScreen
import com.itd.app.ui.screens.feed.FeedViewModel
import com.itd.app.ui.screens.notifications.NotificationsScreen
import com.itd.app.ui.screens.notifications.NotificationsViewModel
import com.itd.app.ui.screens.postdetail.PostDetailScreen
import com.itd.app.ui.screens.postdetail.PostDetailViewModel
import com.itd.app.ui.screens.profile.FollowListScreen
import com.itd.app.ui.screens.profile.ProfileScreen
import com.itd.app.ui.screens.profile.ProfileViewModel
import com.itd.app.ui.screens.qr.QrLoginScreen
import com.itd.app.ui.screens.qr.QrLoginViewModel
import com.itd.app.ui.screens.search.SearchScreen
import com.itd.app.ui.screens.search.SearchViewModel
import com.itd.app.ui.screens.settings.BlockedUsersScreen
import com.itd.app.ui.screens.settings.ChangePasswordScreen
import com.itd.app.ui.screens.settings.DeleteAccountScreen
import com.itd.app.ui.screens.settings.DiagnosticsScreen
import com.itd.app.ui.screens.settings.SecurityScreen
import com.itd.app.ui.screens.settings.SubscriptionScreen
import com.itd.app.ui.screens.settings.EditProfileScreen
import com.itd.app.ui.screens.settings.NotificationSettingsScreen
import com.itd.app.ui.screens.settings.PaymentMethodsScreen
import com.itd.app.ui.screens.settings.PrivacySettingsScreen
import com.itd.app.ui.screens.settings.SessionsScreen
import com.itd.app.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

private val tabRoutes = setOf("feed", "shop", "notifications", "profile")

@Composable
fun MainScreen(
    container: AppContainer,
    overlay: OverlayComponent,
    user: User?,
    userId: String?,
    onLogout: () -> Unit,
    pendingLink: String? = null,
    onLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "feed"
    val username = user?.username.orEmpty()
    val sessionUserId = user?.id ?: userId

    val notifications: NotificationsViewModel = viewModel(
        factory = viewModelFactory { NotificationsViewModel(container.notificationsApi, container.usersApi) },
    )
    val unread by notifications.unreadCount.collectAsStateWithLifecycle()
    val currentRoute by androidx.compose.runtime.rememberUpdatedState(route)

    // Live notifications over SSE while signed in.
    DisposableEffect(sessionUserId) {
        if (sessionUserId == null) return@DisposableEffect onDispose {}
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val stream = NotificationStream(container.apiClient.okHttpClient) { item, sound ->
            ShownNotifications.markShown(item.id)
            handler.post {
                notifications.onLiveNotification(item)
                HalloweenNotificationSounds[item.type]?.let { HalloweenSounds.play(it, forced = false) }
                if (currentRoute != "notifications") showNotification(context, item, sound)
            }
        }
        stream.start()
        notifications.refreshCount()
        onDispose { stream.stop() }
    }

    // Seasonal event lifecycle.
    DisposableEffect(sessionUserId) {
        if (sessionUserId != null) HalloweenPortalManager.start(sessionUserId, container.halloweenRepository)
        onDispose { HalloweenPortalManager.reset() }
    }
    val portalState by HalloweenPortalManager.state.collectAsState()
    LaunchedEffect(sessionUserId, user?.username, portalState.actionsAvailable, portalState.event?.lifecycleEpoch) {
        HalloweenNotices.start(
            sessionUserId,
            user?.username,
            portalState.actionsAvailable,
            portalState.event?.lifecycleEpoch ?: 0,
            container.halloweenRepository,
        ) { HalloweenArt.prefetch(context) }
    }

    val openPost: (Post) -> Unit = { overlay.openPost(it) }
    val openProfile: (String) -> Unit = { handle ->
        if (handle == username) nav.navigateTab("profile") else overlay.openProfile(handle)
    }
    val openPostById: (String, String?) -> Unit = { id, commentId ->
        scope.launch {
            try {
                overlay.openPost(container.postsApi.post(id).data, commentId)
            } catch (_: Exception) {
                Toast.makeText(context, "Не удалось загрузить пост", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val openHashtag: (String) -> Unit = { nav.navigate("hashtag/${Uri.encode(it)}") }
    val openPortal: () -> Unit = {
        portalState.portal?.takeIf { it.active && it.url.isNotBlank() }?.let { overlay.openPortal(it.url, it.title) }
    }

    DisposableEffect(Unit) {
        HalloweenNavigation.openPost = { _, postId -> openPostById(postId, null) }
        HalloweenNavigation.openProfile = openProfile
        HalloweenNavigation.openEventSection = { section -> openEventSection(container, overlay, portalState, section) }
        HalloweenNavigation.sharePost = { author, postId, text ->
            val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "$text\nhttps://итд.com/@$author/post/$postId")
            context.startActivity(Intent.createChooser(share, "Поделиться"))
        }
        onDispose {
            HalloweenNotices.start(null, null, false, 0, container.halloweenRepository) {}
            HalloweenFeed.stop()
        }
    }

    LaunchedEffect(pendingLink) {
        val url = pendingLink ?: return@LaunchedEffect
        when (val link = AppLinkParser.parse(url)) {
            is AppLink.Profile -> openProfile(link.username)
            is AppLink.Post -> openPostById(link.id, link.commentId)
            is AppLink.Qr -> nav.navigate("qr?url=${Uri.encode(link.url)}")
            AppLink.Sessions -> nav.navigate("sessions")
            AppLink.Shop -> nav.navigateTab("shop")
            is AppLink.Event -> openEventSection(container, overlay, portalState, link.link)
            null -> Unit
        }
        onLinkConsumed()
    }

    val interactorMessage by container.postInteractor.message.collectAsStateWithLifecycle()
    val phoneVerification by container.postInteractor.phoneVerification.collectAsStateWithLifecycle()
    LaunchedEffect(interactorMessage) {
        interactorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            container.postInteractor.consumeMessage()
        }
    }
    if (phoneVerification && sessionUserId != null) {
        com.itd.app.ui.components.PhoneVerificationDialog(sessionUserId) { container.postInteractor.dismissPhoneVerification() }
    }

    if (portalState.actionsAvailable) com.itd.app.halloween.HalloweenIntroDialog()

    val stack by overlay.stack.subscribeAsState()
    val overlayOpen = stack.active.instance !is OverlayChild.None

    androidx.compose.runtime.CompositionLocalProvider(com.itd.app.ui.components.LocalPostInteractor provides container.postInteractor) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(nav, startDestination = "feed", modifier = Modifier.fillMaxSize()) {
            composable("feed") {
                val feed: FeedViewModel = viewModel(factory = viewModelFactory { FeedViewModel(container.postsApi) })
                FeedScreen(
                    feed,
                    container.analytics.dwell,
                    PostStatsPoller.get(container.postsApi),
                    openPost,
                    openProfile,
                    openHashtag,
                    onSearch = { nav.navigate("search") },
                )
            }
            composable("shop") {
                ItdWebPage("https://xn--d1ah4a.com/public/shop/", container.tokenStore, container.apiClient, Modifier.statusBarsPadding())
            }
            composable("notifications") {
                NotificationsScreen(
                    notifications,
                    active = route == "notifications",
                    onOpen = { item -> openNotification(item, openProfile, openPostById) },
                    onProfile = { item -> openProfile(item.actor.username ?: item.actor.id) },
                )
            }
            composable("profile") {
                val profile: ProfileViewModel = viewModel(factory = viewModelFactory { ProfileViewModel(container.usersApi, container.postsApi, username) })
                ProfileScreen(
                    profile,
                    container.analytics.dwell,
                    PostStatsPoller.get(container.postsApi),
                    true,
                    openPost,
                    openProfile,
                    { nav.navigate("settings") },
                    profile::toggleLike,
                    onEditProfile = { nav.navigate("edit-profile") },
                    onFollowList = { id, followers -> nav.navigate("follows/$id?followers=$followers") },
                    onCompose = { wall -> nav.navigate("create-post?wall=${wall.orEmpty()}") },
                    onSubscription = { nav.navigate("subscription") },
                )
            }
            composable("follows/{id}?followers={followers}", arguments = listOf(navArgument("followers") { defaultValue = "true" })) { backStack ->
                FollowListScreen(
                    container,
                    requireNotNull(backStack.arguments?.getString("id")),
                    backStack.arguments?.getString("followers") != "false",
                    sessionUserId,
                    openProfile,
                ) { nav.popBackStack() }
            }
            composable("search") {
                val search: SearchViewModel = viewModel(factory = viewModelFactory { SearchViewModel(container.searchApi) })
                SearchScreen(search, openProfile, openHashtag) { nav.popBackStack() }
            }
            composable("hashtag/{name}") { backStack ->
                HashtagScreen(container, requireNotNull(backStack.arguments?.getString("name")), openPost, openProfile)
            }
            composable("create-post?wall={wall}", arguments = listOf(navArgument("wall") { defaultValue = "" })) { backStack ->
                val wall = backStack.arguments?.getString("wall")?.takeIf(String::isNotBlank)
                val create: CreatePostViewModel = viewModel(factory = viewModelFactory {
                    CreatePostViewModel(container.postsApi, container.mediaRepository, wall, container.halloweenRepository, container.postInteractor::requirePhoneVerification)
                })
                CreatePostScreen(create, { nav.popBackStack() }) { id ->
                    nav.popBackStack()
                    openPostById(id, null)
                }
            }
            composable("qr?url={url}", arguments = listOf(navArgument("url") { defaultValue = "" })) { backStack ->
                val qr: QrLoginViewModel = viewModel(factory = viewModelFactory { QrLoginViewModel(container.authApi) })
                LaunchedEffect(qr) { backStack.arguments?.getString("url")?.takeIf(String::isNotBlank)?.let(qr::onLink) }
                QrLoginScreen(qr) { nav.popBackStack() }
            }
            composable("settings") { SettingsScreen({ nav.navigate(it) }, { nav.popBackStack() }, onLogout) }
            composable("edit-profile") { EditProfileScreen(container, username, { nav.popBackStack() }, onEditBanner = overlay::openBannerPainter) }
            composable("security") { SecurityScreen({ nav.navigate(it) }, { nav.popBackStack() }) }
            composable("sessions") { SessionsScreen(container, { nav.popBackStack() }) }
            composable("privacy") { PrivacySettingsScreen(container, { nav.navigate(it) }, { nav.popBackStack() }) }
            composable("blocked") { BlockedUsersScreen(container, { nav.popBackStack() }) }
            composable("notification-settings") { NotificationSettingsScreen(container, { nav.popBackStack() }) }
            composable("change-password") { ChangePasswordScreen(container, { nav.popBackStack() }) }
            composable("delete-account") { DeleteAccountScreen(container, { nav.popBackStack() }, onLogout) }
            composable("subscription") { SubscriptionScreen(container, { nav.navigate(it) }, { nav.popBackStack() }) }
            composable("payment-methods") { PaymentMethodsScreen(container, { nav.popBackStack() }) }
            composable("diagnostics") { DiagnosticsScreen(container, { nav.popBackStack() }) }
        }

        if (route in tabRoutes && !overlayOpen) {
            ItdBottomBar(
                current = route,
                tabs = bottomTabs(unread),
                portalTitle = portalState.portal?.takeIf { it.active && it.url.isNotBlank() }?.title,
                onSelect = { nav.navigateTab(it) },
                onPortal = openPortal,
                onCreatePost = { nav.navigate("create-post?wall=") },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }

        Children(stack = overlay.stack, modifier = Modifier.fillMaxSize()) { child ->
            when (val instance = child.instance) {
                OverlayChild.None -> Unit
                is OverlayChild.PostDetail -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val detail: PostDetailViewModel = viewModel(
                        key = "post-${child.configuration.hashCode()}",
                        factory = viewModelFactory {
                            PostDetailViewModel(container.postsApi, instance.post.id, container.mediaRepository, container.postInteractor::requirePhoneVerification)
                        },
                    )
                    PostDetailScreen(detail, openProfile, instance.targetCommentId, sessionUserId, onBack = overlay::pop, onHashtag = { overlay.pop(); openHashtag(it) })
                }
                is OverlayChild.Profile -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val profile: ProfileViewModel = viewModel(
                        key = "profile-${child.configuration.hashCode()}",
                        factory = viewModelFactory { ProfileViewModel(container.usersApi, container.postsApi, instance.username) },
                    )
                    ProfileScreen(
                        profile,
                        container.analytics.dwell,
                        PostStatsPoller.get(container.postsApi),
                        instance.isOwnProfile,
                        openPost,
                        openProfile,
                        { nav.navigate("settings") },
                        profile::toggleLike,
                        onBack = overlay::pop,
                        onFollowList = { id, followers ->
                            overlay.pop()
                            nav.navigate("follows/$id?followers=$followers")
                        },
                        onCompose = { wall ->
                            overlay.pop()
                            nav.navigate("create-post?wall=${wall.orEmpty()}")
                        },
                    )
                }
                is OverlayChild.Portal -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ItdWebPage(instance.url, container.tokenStore, container.apiClient, Modifier.statusBarsPadding(), canClose = true, onClose = overlay::pop)
                }
                OverlayChild.BannerPainter -> BannerPainterScreen(container, onClose = overlay::pop)
            }
        }

        HalloweenToastHost(Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo("feed") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun openNotification(
    item: NotificationItem,
    openProfile: (String) -> Unit,
    openPostById: (String, String?) -> Unit,
) {
    val target = item.targetId
    when {
        item.type == "follow" -> openProfile(item.actor.username ?: item.actor.id)
        target != null -> {
            val comment = if (item.type == "like") item.subjectId?.takeIf { item.subjectType == "comment" } else item.subjectId
            openPostById(target, comment)
        }
        else -> openProfile(item.actor.username ?: item.actor.id)
    }
}

/** Opens the event page (optionally a section) in the portal overlay. */
private fun openEventSection(container: AppContainer, overlay: OverlayComponent, state: HalloweenPortalManager.State, section: String?) {
    val base = "https://xn--d1ah4a.com/public/events/halloween/"
    val url = when {
        section == null -> base
        section.startsWith("/") -> "https://xn--d1ah4a.com$section"
        section.startsWith("https://") -> section
        else -> "$base#$section"
    }
    overlay.openPortal(url, state.portal?.title ?: "Ивент")
}
