package com.itd.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itd.app.R
import com.itd.app.data.model.TrendingHashtag
import com.itd.app.ui.common.EmptyState
import com.itd.app.ui.components.UserAvatar
import com.itd.app.util.TimeFormat

@Composable
fun SearchScreen(viewModel: SearchViewModel, onProfile: (String) -> Unit, onHashtag: (String) -> Unit, onBack: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_chevron_back), "Назад", Modifier.size(40.dp).clickable(onClick = onBack).padding(9.dp))
            OutlinedTextField(
                state.query,
                viewModel::search,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Поиск") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(50),
            )
            if (state.query.isNotEmpty()) TextButton(onClick = viewModel::clear) { Text("Очистить") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = colors.error, modifier = Modifier.padding(16.dp)) }
        LazyColumn(Modifier.fillMaxSize()) {
            if (state.query.isBlank()) {
                if (state.trending.isNotEmpty()) item { SectionTitle("Популярные хэштеги") }
                items(state.trending, key = { "t-" + it.id }) { HashtagRow(it, onHashtag) }
            } else {
                if (!state.loading && state.data.users.isEmpty() && state.data.hashtags.isEmpty()) {
                    item { EmptyState("Ничего не найдено") }
                }
                if (state.data.users.isNotEmpty()) item { SectionTitle("Пользователи") }
                items(state.data.users, key = { it.id }) { user ->
                    Row(Modifier.fillMaxWidth().clickable { onProfile(user.username ?: user.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(user.avatar, user.displayName)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(user.displayName, fontWeight = FontWeight.SemiBold)
                            Text("@${user.username.orEmpty()}", color = colors.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
                if (state.data.hashtags.isNotEmpty()) item { SectionTitle("Хэштеги") }
                items(state.data.hashtags, key = { "h-" + it.id }) { HashtagRow(it, onHashtag) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp)
}

@Composable
private fun HashtagRow(hashtag: TrendingHashtag, onHashtag: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onHashtag(hashtag.name) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("#${hashtag.name}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        if (hashtag.postsCount > 0) {
            Text("${hashtag.postsCount} ${TimeFormat.plural(hashtag.postsCount, "пост", "поста", "постов")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
