package com.itd.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.R
import com.itd.app.data.model.FollowListUser
import com.itd.app.di.AppContainer
import com.itd.app.ui.common.EmptyState
import com.itd.app.ui.components.ItdTextField
import com.itd.app.ui.components.UserAvatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun FollowListScreen(
    container: AppContainer,
    userId: String,
    startWithFollowers: Boolean,
    ownUserId: String?,
    onProfile: (String) -> Unit,
    onBack: () -> Unit,
) {
    var followers by rememberSaveable { mutableStateOf(startWithFollowers) }
    var users by remember(followers) { mutableStateOf<List<FollowListUser>>(emptyList()) }
    var page by remember(followers) { mutableStateOf(1) }
    var hasMore by remember(followers) { mutableStateOf(true) }
    var loading by remember(followers) { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val following = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        if (loading || !hasMore) return
        loading = true
        try {
            val response = if (followers) container.usersApi.followers(userId, page) else container.usersApi.following(userId, page)
            users = (users + response.data.users).distinctBy { it.id }
            hasMore = response.data.pagination.hasMore
            page += 1
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            hasMore = false
        } finally {
            loading = false
        }
    }
    LaunchedEffect(followers) { load() }
    val visible = users.filter {
        query.isBlank() || it.displayName.contains(query, ignoreCase = true) || it.username.orEmpty().contains(query, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_chevron_back), "Назад", Modifier.size(22.dp))
            }
            listOf(true to "Подписчики", false to "Подписки").forEach { (value, title) ->
                Column(Modifier.weight(1f).clickable { followers = value }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, Modifier.padding(vertical = 10.dp), fontWeight = if (followers == value) FontWeight.SemiBold else FontWeight.Normal)
                    Box(Modifier.height(2.dp).width(48.dp).background(if (followers == value) MaterialTheme.colorScheme.primary else Color.Transparent))
                }
            }
            Spacer(Modifier.width(44.dp))
        }
        ItdTextField(query, { query = it }, "Поиск", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (!loading && visible.isEmpty()) {
                item { EmptyState(if (query.isNotBlank()) "Ничего не найдено" else "Пусто") }
            }
            items(visible, key = { it.id }) { user ->
                Row(
                    Modifier.fillMaxWidth().clickable { onProfile(user.username ?: user.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(user.avatar, user.displayName)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (user.verified) Icon(painterResource(R.drawable.ic_verified), "Verified", Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("@${user.username.orEmpty()}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (user.id != ownUserId) {
                        val isFollowing = following[user.id] ?: user.isFollowing
                        Text(
                            if (isFollowing) "Отписаться" else "Подписаться",
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (isFollowing) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onBackground)
                                .clickable {
                                    following[user.id] = !isFollowing
                                    scope.launch {
                                        try {
                                            if (isFollowing) container.usersApi.unfollow(user.id) else container.usersApi.follow(user.id)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            following[user.id] = isFollowing
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            color = if (isFollowing) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            if (hasMore && users.isNotEmpty()) {
                item {
                    LaunchedEffect(page) { load() }
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}
