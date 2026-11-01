package com.itd.app.ui.screens.profile

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.itd.app.ui.components.PostCard
import com.itd.app.ui.components.ReportDialog
import com.itd.app.ui.screens.notifications.nukstaBrush
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
            LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), state = listState) {
                item {
                    ProfileHeader(
                        profile = profile,
                        isOwn = isOwn,
                        followBusy = content.followBusy,
                        onBack = onBack,
                        onSettings = onSettings,
                        onEdit = onEditProfile,
                        onFollow = viewModel::toggleFollow,
                        onBlock = viewModel::toggleBlock,
                        onReport = viewModel::report,
                        onFollowList = { followers -> onFollowList(profile.id, followers) },
                        onSubscription = onSubscription,
                    )
                }
                if (!profile.isBlockedByMe) {
                    val canWrite = isOwn || profile.wallAccess == "everyone" ||
                        (profile.wallAccess == "followers" && profile.isFollowing) ||
                        (profile.wallAccess == "mutual" && profile.isFollowing && profile.isFollowedBy)
                    if (canWrite) {
                        item {
                            Text(
                                if (isOwn) "Что нового?" else "Напишите на стене...",
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                                    .clickable { onCompose(if (isOwn) null else profile.id) }
                                    .padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item { ProfileTabs(content.tab, isOwn || profile.likesVisibility != "nobody", viewModel::selectTab) }
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
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: UserProfile,
    isOwn: Boolean,
    followBusy: Boolean,
    onBack: (() -> Unit)?,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
    onFollow: () -> Unit,
    onBlock: () -> Unit,
    onReport: (String) -> Unit,
    onFollowList: (Boolean) -> Unit,
    onSubscription: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.fillMaxWidth().height(150.dp).background(colors.surfaceVariant)) {
            profile.banner?.let { AsyncImage(it, "Banner", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            if (onBack != null) CircleIconButton(R.drawable.ic_chevron_back, "Back", onBack)
            Spacer(Modifier.weight(1f))
            if (isOwn) {
                CircleIconButton(R.drawable.ic_settings_gear, "Настройки", onSettings)
            } else {
                Box {
                    CircleIconButton(R.drawable.ic_more, "More") { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Пожаловаться") }, { menu = false; reporting = true })
                        DropdownMenuItem(
                            { Text(if (profile.isBlockedByMe) "Разблокировать" else "Заблокировать", color = colors.error) },
                            { menu = false; onBlock() },
                        )
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val avatarBorder = if (profile.hasNuksta) Modifier.border(3.dp, nukstaBrush(), CircleShape) else Modifier.border(3.dp, colors.background, CircleShape)
        Box(
            Modifier.offset(y = (-40).dp).size(84.dp).then(avatarBorder).clip(CircleShape).background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val avatar = profile.avatar
            if (avatar != null && avatar.startsWith("http")) {
                AsyncImage(avatar, profile.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(avatar ?: "?", fontSize = 40.sp)
            }
        }
        Column(Modifier.offset(y = (-28).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (profile.verified) {
                    Icon(painterResource(R.drawable.ic_verified), "Verified", Modifier.padding(start = 4.dp).size(18.dp), tint = colors.primary)
                }
                profile.pin?.let { pin ->
                    Text(pin.name, Modifier.padding(start = 6.dp), fontSize = 16.sp)
                }
                com.itd.app.halloween.PumpkinBadge(profile.halloween, profile.displayName, 22.dp)
            }
            Text("@${profile.username.orEmpty()}", color = colors.onSurfaceVariant)
            if (profile.hasNuksta) {
                Text(
                    "ИТД НУКСТА",
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(nukstaBrush()).clickable(onClick = onSubscription)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            profile.bio?.takeIf { it.isNotBlank() }?.let {
                Text(it.replace(Regex("\\s*\\n\\s*"), "\n"), Modifier.padding(top = 10.dp), fontSize = 15.sp)
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Counter(profile.followersCount, "подписчиков") { onFollowList(true) }
                Counter(profile.followingCount, "подписок") { onFollowList(false) }
            }
            if (!profile.online && profile.lastSeen != null) {
                InfoLine(R.drawable.ic_clock, "Был(а) в сети " + lastSeenText(profile.lastSeen.unit, profile.lastSeen.value))
            }
            profile.createdAt?.let(::registrationText)?.let { InfoLine(R.drawable.ic_calendar, "Регистрация: $it") }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    isOwn -> PillButton("Редактировать", filled = false, modifier = Modifier.weight(1f), onClick = onEdit)
                    profile.isBlockedByMe -> PillButton("Разблокировать", filled = true, modifier = Modifier.weight(1f), onClick = onBlock)
                    else -> {
                        val label = when {
                            profile.isFollowing && profile.isFollowedBy -> "Друзья"
                            profile.isFollowing -> "Вы подписаны"
                            profile.isFollowedBy -> "Подписаться в ответ"
                            else -> "Подписаться"
                        }
                        PillButton(label, filled = !profile.isFollowing, enabled = !followBusy, modifier = Modifier.weight(1f), onClick = onFollow)
                    }
                }
                Box {
                    var shareMenu by remember { mutableStateOf(false) }
                    val url = "https://итд.com/@${profile.username.orEmpty()}"
                    CircleIconButton(R.drawable.ic_share, "Поделиться") { shareMenu = true }
                    DropdownMenu(shareMenu, { shareMenu = false }) {
                        DropdownMenuItem({ Text("Копировать ссылку") }, {
                            shareMenu = false
                            com.itd.app.ui.components.copyLink(context, url)
                        })
                        DropdownMenuItem({ Text("Поделиться профилем") }, {
                            shareMenu = false
                            val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                            context.startActivity(Intent.createChooser(share, "Поделиться"))
                        })
                    }
                }
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
    Row(Modifier.clickable(onClick = onClick), verticalAlignment = Alignment.Bottom) {
        Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(" $label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
    }
}

@Composable
private fun InfoLine(icon: Int, text: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun ProfileTabs(selected: ProfileTab, showLiked: Boolean, onSelect: (ProfileTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth()) {
            ProfileTab.entries.filter { it == ProfileTab.Posts || showLiked }.forEach { tab ->
                Column(Modifier.weight(1f).clickable { onSelect(tab) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        tab.title,
                        Modifier.padding(vertical = 12.dp),
                        fontWeight = if (tab == selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (tab == selected) colors.onBackground else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Box(Modifier.height(2.dp).width(48.dp).background(if (tab == selected) colors.primary else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}
