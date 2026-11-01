package com.itd.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itd.app.data.model.NotificationItem
import com.itd.app.util.TimeFormat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border

private fun actionText(type: String): String = when (type) {
    "follow" -> "подписался(-ась) на вас"
    "wall_post" -> "оставил(а) запись на вашей стене"
    "comment" -> "прокомментировал(а) вашу запись"
    "mention" -> "упомянул(а) вас"
    "comment_like" -> "оценил(а) ваш комментарий"
    "reply" -> "ответил(а) на ваш комментарий"
    "like" -> "оценил(а) вашу запись"
    "repost" -> "поделился(-ась) вашей записью"
    else -> ""
}

/** Gradient ring around avatars of users with an ITD Nuksta subscription. */
@Composable
fun nukstaBrush(): Brush = if (isSystemInDarkTheme()) {
    Brush.linearGradient(listOf(Color(0xFFB3AFFA), Color(0xFF52BEF7)))
} else {
    Brush.linearGradient(listOf(Color(0xFF2988DA), Color(0xFF0F55D1)))
}

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    active: Boolean,
    onOpen: (NotificationItem) -> Unit,
    onProfile: (NotificationItem) -> Unit,
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val followStates by viewModel.followStates.collectAsStateWithLifecycle()
    val inProgress by viewModel.followInProgress.collectAsStateWithLifecycle()
    var needsLoad by rememberSaveable { mutableStateOf(!active) }
    if (!active) needsLoad = true
    LaunchedEffect(active) {
        if (active && needsLoad) {
            needsLoad = false
            viewModel.load()
        }
    }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, hasMore, isLoadingMore) {
        if (nearEnd && hasMore && !isLoadingMore && !isLoading) viewModel.loadMore()
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(top = top + 8.dp, bottom = 100.dp)) {
            if (isLoading && notifications.isEmpty()) {
                items(8) { NotificationSkeleton() }
            } else {
                items(notifications, key = { it.id }) { item ->
                    NotificationRow(
                        item = item,
                        followStates = followStates,
                        inProgress = inProgress,
                        onToggleFollow = viewModel::toggleFollow,
                        onClick = { onOpen(item) },
                        onAvatar = { onProfile(item) },
                    )
                }
                if (isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    followStates: Map<String, Boolean>,
    inProgress: Set<String>,
    onToggleFollow: (String, Boolean) -> Unit,
    onClick: () -> Unit,
    onAvatar: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (item.read) Color.Transparent else colors.surfaceVariant.copy(alpha = 0.5f)
    Row(
        Modifier.fillMaxWidth().background(background).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarModifier = Modifier.size(40.dp).let {
            if (item.actor.hasNuksta) it.border(2.dp, nukstaBrush(), CircleShape) else it
        }
        Box(
            avatarModifier.clip(CircleShape).background(colors.surfaceVariant).clickable(onClick = onAvatar),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.actor.avatar ?: "?", fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.onBackground, fontWeight = FontWeight.SemiBold)) { append(item.actor.displayName) }
                    append(" ")
                    withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(actionText(item.type)) }
                },
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.preview.isNullOrBlank()) {
                Text(
                    item.preview,
                    Modifier.padding(top = 1.dp),
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(TimeFormat.relative(item.createdAt), color = colors.outline, fontSize = 12.sp)
        }
        if (item.type == "follow") {
            Spacer(Modifier.width(8.dp))
            FollowBackButton(item.actor.id, item.actor.isFollowing == true, item.actor.isFollowedBy == true, followStates, inProgress, onToggleFollow)
        } else if (!item.read) {
            Box(Modifier.padding(start = 8.dp, top = 6.dp).size(8.dp).clip(CircleShape).background(colors.primary))
        }
    }
}

@Composable
fun FollowBackButton(
    userId: String,
    isFollowing: Boolean,
    isFollowedBy: Boolean,
    followStates: Map<String, Boolean>,
    inProgress: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val following = followStates[userId] ?: isFollowing
    val busy = userId in inProgress
    val label = when {
        following -> "Подписан"
        isFollowedBy -> "Подписаться в ответ"
        else -> "Подписаться"
    }
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (following) colors.onBackground.copy(alpha = 0.1f) else colors.onBackground)
            .clickable(enabled = !busy) { onToggle(userId, following) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = if (following) colors.onBackground else colors.background)
        } else {
            Text(label, color = if (following) colors.onBackground else colors.background, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun NotificationSkeleton() {
    val shade = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(shade))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shade))
            Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(shade))
        }
    }
}
