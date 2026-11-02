package com.itd.app.ui.screens.profile

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.itd.app.R
import com.itd.app.data.analytics.DwellTracker
import com.itd.app.data.analytics.ImpressionSource
import com.itd.app.data.model.Post
import com.itd.app.data.model.UserProfile
import com.itd.app.data.repository.PostStatsPoller
import com.itd.app.ui.common.EmptyState
import com.itd.app.ui.common.ErrorContent
import com.itd.app.ui.common.LoadState
import com.itd.app.ui.common.LoadingContent
import com.itd.app.ui.common.TrackPostImpressions
import com.itd.app.ui.components.ItdMenu
import com.itd.app.ui.components.ItdMenuDivider
import com.itd.app.ui.components.ItdMenuItem
import com.itd.app.ui.components.PillTabs
import com.itd.app.ui.components.PinIcon
import com.itd.app.ui.components.PostCard
import com.itd.app.ui.components.ReportDialog
import com.itd.app.ui.components.UserName
import com.itd.app.ui.components.noRippleClickable
import com.itd.app.util.TimeFormat
import java.time.ZoneId

private val months = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")

fun lastSeenText(unit: String?, value: Int?): String {
    val n = value ?: 0
    fun ago(one: String, few: String, many: String) = "$n ${TimeFormat.plural(n, one, few, many)} назад"
    return when (unit) {
        "minutes" -> ago("минуту", "минуты", "минут")
        "hours" -> ago("час", "часа", "часов")
        "days" -> ago("день", "дня", "дней")
        "weeks" -> ago("неделю", "недели", "недель")
        "months" -> ago("месяц", "месяца", "месяцев")
        "years" -> ago("год", "года", "лет")
        "recently" -> "недавно"
        else -> "давно"
    }
}

fun registrationText(createdAt: String): String? = runCatching {
    val date = TimeFormat.parse(createdAt).atZone(ZoneId.systemDefault())
    "${months[date.monthValue - 1]} ${date.year} г."
}.getOrNull()


private val OnlineGreen = Color(0xFF4CAF50)
private val LogoutRed = Color(0xFFFF453A)

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    dwell: DwellTracker,
    stats: PostStatsPoller?,
    isOwn: Boolean,
    onPost: (Post) -> Unit,
    onProfile: (String) -> Unit,
    onSettings: () -> Unit,
    onLike: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onEditProfile: () -> Unit = {},
    onFollowList: (userId: String, followers: Boolean) -> Unit = { _, _ -> },
    onCompose: (wallRecipientId: String?) -> Unit = {},
    onSubscription: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }
    val listState = rememberLazyListState()
    when (val value = state) {
        LoadState.Loading -> LoadingContent()
        is LoadState.Error -> ErrorContent(value.message, { viewModel.refresh() })
        is LoadState.Ready -> {
            val content = value.data
            val profile = content.profile
            TrackPostImpressions(listState, content.posts, ImpressionSource.PROFILE, profile.id, dwell, stats)
            val nearEnd by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
                }
            }
            LaunchedEffect(nearEnd, content.tab) { if (nearEnd) viewModel.loadMore() }
            val colors = MaterialTheme.colorScheme
            Box(Modifier.fillMaxSize().background(colors.background)) {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    item(key = "header") {
                        Box {
                            ProfileBackdrop(profile.banner)
                            Column(Modifier.statusBarsPadding().padding(top = 14.dp)) {
                                ProfileBanner(profile.banner, profile.avatar, profile.online)
                                ProfileInfo(
                                    profile = profile,
                                    isOwn = isOwn,
                                    followBusy = content.followBusy,
                                    onEdit = onEditProfile,
                                    onSettings = onSettings,
                                    onLogout = onLogout,
                                    onFollow = viewModel::toggleFollow,
                                    onBlock = viewModel::toggleBlock,
                                    onFollowList = { followers -> onFollowList(profile.id, followers) },
                                    onSubscription = onSubscription,
                                )
                            }
                        }
                    }
                    if (!profile.isBlockedByMe) {
                        item(key = "tabs_top_divider") { HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant) }
                        item(key = "tabs") {
                            val tabs = ProfileTab.entries.filter { it == ProfileTab.Posts || isOwn || profile.likesVisibility != "nobody" }
                            Box(Modifier.fillMaxWidth().height(62.dp).background(colors.background)) {
                                PillTabs(
                                    tabs.map { it.title },
                                    tabs.indexOf(content.tab).coerceAtLeast(0),
                                    { viewModel.selectTab(tabs[it]) },
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp).height(46.dp),
                                )
                            }
                        }
                        item(key = "profile_tabs_divider") { HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant) }
                        val canWrite = profile.wallAccess == "everyone" ||
                            (profile.wallAccess == "followers" && profile.isFollowing) ||
                            (profile.wallAccess == "mutual" && profile.isFollowing && profile.isFollowedBy)
                        if (content.tab == ProfileTab.Posts && !isOwn && canWrite) {
                            item(key = "wall_write_prompt") {
                                Text(
                                    "Напишите на стене...",
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clip(RoundedCornerShape(16.dp)).background(colors.surface)
                                        .clickable { onCompose(profile.id) }
                                        .padding(16.dp),
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        val page = content.current
                        if (page.loaded && page.posts.isEmpty()) {
                            item { EmptyState(if (content.tab == ProfileTab.Posts) "Нет постов" else "Нет лайкнутых постов") }
                        }
                        items(page.posts, key = { it.id }) { post ->
                            PostCard(post, { onPost(post) }, { onProfile(post.author.username ?: post.author.id) }, onOpenOriginal = onPost)
                        }
                        if (page.loading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(100.dp)) }
                    }
                }
                if (onBack != null || !isOwn) {
                    ProfileTopBar(profile, onBack, viewModel::toggleBlock, viewModel::report)
                }
            }
        }
    }
}

/** Blurred, dimmed copy of the banner that tints the top of the screen. */
@Composable
private fun ProfileBackdrop(banner: String?) {
    if (banner == null) return
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 340.dp)) {
        AsyncImage(
            banner,
            null,
            Modifier.fillMaxSize().let { if (Build.VERSION.SDK_INT >= 31) it.blur(110.dp, BlurredEdgeTreatment.Unbounded) else it }.alpha(0.34f),
            contentScale = ContentScale.FillBounds,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.34f to Color.Transparent, 1f to background)))
    }
}

/** Rounded banner card with the avatar overlapping its bottom edge. */
@Composable
private fun ProfileBanner(banner: String?, avatar: String?, online: Boolean) {
    val colors = MaterialTheme.colorScheme
    val placeholder = Brush.verticalGradient(listOf(colors.surfaceVariant, colors.surface))
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 44.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(80f / 29f).clip(RoundedCornerShape(20.dp)).background(placeholder)) {
                if (banner != null) AsyncImage(banner, "Banner", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).size(88.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(colors.surfaceVariant.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                if (avatar != null && avatar.startsWith("http")) AsyncImage(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(avatar ?: "?", fontSize = 40.sp)
            }
            if (online) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset((-4).dp, (-4).dp).size(18.dp).drawBehind {
                        val radius = size.minDimension / 2
                        drawCircle(Color.Transparent, radius, blendMode = BlendMode.Clear)
                        drawCircle(OnlineGreen, radius - 2.dp.toPx())
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileInfo(
    profile: UserProfile,
    isOwn: Boolean,
    followBusy: Boolean,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onFollow: () -> Unit,
    onBlock: () -> Unit,
    onFollowList: (Boolean) -> Unit,
    onSubscription: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserName(profile.displayName, profile.hasNuksta, 20.sp, fontWeight = FontWeight.Bold)
            if (profile.verified) {
                Spacer(Modifier.width(5.dp))
                Icon(painterResource(R.drawable.ic_verified), "Verified", Modifier.size(18.dp), tint = Color.Unspecified)
            }
            profile.pin?.let {
                Spacer(Modifier.width(5.dp))
                PinIcon(it, 22.dp)
            }
            com.itd.app.halloween.PumpkinBadge(profile.halloween, profile.displayName)
        }
        Spacer(Modifier.height(2.dp))
        Text("@${profile.username.orEmpty()}", color = colors.onSurfaceVariant, fontSize = 14.sp)
        profile.bio?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Text(it.replace(Regex("\\s*\\n\\s*"), " ").trim(), color = colors.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Counter(profile.followersCount, "подписчиков") { onFollowList(true) }
            Box(Modifier.size(4.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = 0.3f)))
            Counter(profile.followingCount, "подписок") { onFollowList(false) }
        }
        Spacer(Modifier.height(14.dp))
        if (!isOwn) {
            FollowButton(profile.isFollowing, profile.isFollowedBy, profile.isBlockedByMe, enabled = !followBusy) {
                if (profile.isBlockedByMe) onBlock() else onFollow()
            }
        } else {
            var menu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth().clip(CircleShape).background(colors.surface).noRippleClickable { menu = true }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_pencil_edit), null, Modifier.size(13.dp), tint = colors.onSurface)
                            Spacer(Modifier.width(6.dp))
                            Text("Редактировать", color = colors.onSurface, fontSize = 14.sp)
                        }
                    }
                    ItdMenu(menu, { menu = false }) {
                        ItdMenuItem("Редактировать профиль", R.drawable.ic_pencil_edit) { menu = false; onEdit() }
                        ItdMenuDivider()
                        ItdMenuItem("Настройки", R.drawable.ic_settings_gear) { menu = false; onSettings() }
                        ItdMenuDivider()
                        ItdMenuItem("Выйти", R.drawable.ic_logout, LogoutRed) { menu = false; onLogout() }
                    }
                }
                Box(
                    Modifier.weight(1f).clip(CircleShape).background(colors.surface).noRippleClickable(onSubscription).padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("ИТД НУКСТА", color = colors.onSurface, fontSize = 14.sp)
                }
                ShareButton(profile.username.orEmpty(), context)
            }
        }
        if (!profile.online && profile.lastSeen != null) {
            Spacer(Modifier.height(10.dp))
            InfoLine(R.drawable.ic_clock, "Был(а) в сети " + lastSeenText(profile.lastSeen.unit, profile.lastSeen.value))
        }
        profile.createdAt?.let(::registrationText)?.let {
            Spacer(Modifier.height(if (!profile.online && profile.lastSeen != null) 4.dp else 10.dp))
            InfoLine(R.drawable.ic_calendar, "Регистрация: $it")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ShareButton(username: String, context: android.content.Context) {
    val colors = MaterialTheme.colorScheme
    var shareMenu by remember { mutableStateOf(false) }
    val url = "https://итд.com/@$username"
    Box(
        Modifier.fillMaxHeight().aspectRatio(1f).clip(CircleShape).background(colors.surface).noRippleClickable { shareMenu = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_share), "Поделиться", Modifier.size(18.dp), tint = colors.onSurface)
        ItdMenu(shareMenu, { shareMenu = false }) {
            ItdMenuItem("Копировать ссылку") {
                shareMenu = false
                com.itd.app.ui.components.copyLink(context, url)
            }
            ItdMenuDivider()
            ItdMenuItem("Поделиться профилем") {
                shareMenu = false
                val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                context.startActivity(Intent.createChooser(share, "Поделиться"))
            }
        }
    }
}

@Composable
private fun FollowButton(following: Boolean, followedBy: Boolean, blocked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        when {
            blocked -> colors.error
            following -> colors.surfaceVariant
            else -> colors.onSurface
        },
        tween(200),
        label = "followBg",
    )
    val content by animateColorAsState(
        when {
            blocked -> Color.White
            following -> colors.onSurface
            else -> colors.surface
        },
        tween(200),
        label = "followText",
    )
    val showPlus = !following && !blocked
    val iconWidth by animateDpAsState(if (showPlus) 20.dp else 0.dp, tween(200), label = "iconWidth")
    val iconAlpha by animateFloatAsState(if (showPlus) 1f else 0f, tween(200), label = "iconAlpha")
    val label = when {
        blocked -> "Разблокировать"
        following && followedBy -> "Друзья"
        following -> "Вы подписаны"
        followedBy -> "Подписаться в ответ"
        else -> "Подписаться"
    }
    Box(
        Modifier.fillMaxWidth().clip(CircleShape).background(background).noRippleClickable { if (enabled) onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconWidth > 0.dp) {
                Icon(painterResource(R.drawable.ic_add), null, Modifier.width(iconWidth).size(18.dp).alpha(iconAlpha), tint = content)
            }
            Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Back and overflow buttons floating over someone else's profile. */
@Composable
private fun ProfileTopBar(profile: UserProfile, onBack: (() -> Unit)?, onBlock: () -> Unit, onReport: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
        if (onBack != null) CircleIconButton(R.drawable.ic_chevron_back, "Back", onBack)
        Spacer(Modifier.weight(1f))
        Box {
            CircleIconButton(R.drawable.ic_more, "More") { menu = true }
            ItdMenu(menu, { menu = false }) {
                ItdMenuItem("Пожаловаться") { menu = false; reporting = true }
                ItdMenuDivider()
                ItdMenuItem(if (profile.isBlockedByMe) "Разблокировать" else "Заблокировать", color = colors.error) { menu = false; onBlock() }
            }
        }
    }
    if (reporting) {
        ReportDialog(onDismiss = { reporting = false }) { reason ->
            reporting = false
            onReport(reason)
        }
    }
}

@Composable
private fun Counter(count: Int, label: String, onClick: () -> Unit) {
    Row(Modifier.noRippleClickable(onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(count.toString(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.width(4.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun InfoLine(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PillButton(text: String, filled: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(50))
            .background(if (filled) colors.onBackground else colors.onBackground.copy(alpha = 0.1f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) colors.background else colors.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun CircleIconButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, Modifier.size(18.dp))
    }
}
