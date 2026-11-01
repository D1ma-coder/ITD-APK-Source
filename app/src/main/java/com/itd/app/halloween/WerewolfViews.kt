package com.itd.app.halloween

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val NightBackground = Color(0xFF14142B)
private val NightAccent = Color(0xFFB3AFFA)

/** "3 ч 20 мин" style duration, rounded up to a minute. */
fun formatDuration(millis: Long): String {
    val minutes = ((millis + 59_999) / 60_000).toInt()
    return listOfNotNull(
        (minutes / 60).takeIf { it > 0 }?.let { "$it ч" },
        (minutes % 60).takeIf { it > 0 }?.let { "$it мин" },
    ).joinToString(" ")
}

/** Milliseconds until the next midnight in Moscow time. */
fun millisToMidnight(now: Long = HalloweenFeed.now()): Long = 86_400_000 - Math.floorMod(now + 10_800_000, 86_400_000L)

/**
 * Header and body override for a werewolf post. Returns the night content to show instead of the
 * day text, or null when the day version should be shown.
 */
@Composable
fun WerewolfHeader(werewolf: HalloweenWerewolf, isOwner: Boolean, flipped: Boolean, onFlip: (Boolean) -> Unit) {
    var help by remember { mutableStateOf(false) }
    val label = when {
        werewolf.night -> "Ночная сторона · до 9:00"
        werewolf.revealed -> "Оборотень"
        else -> "Оборотень · в 00:00"
    }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🐺 $label",
                Modifier.clip(RoundedCornerShape(50)).background(NightBackground).clickable { help = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                color = NightAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            if (!werewolf.night && werewolf.revealed) {
                Text(
                    if (flipped) "Вернуть" else "Перевернуть",
                    Modifier.clip(RoundedCornerShape(50)).clickable { onFlip(!flipped) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        val hint = when {
            werewolf.night -> "Это ночная версия поста. В 9 утра вернётся обычная"
            werewolf.revealed -> if (flipped) null else "Ночная версия уже открыта. Жми «Перевернуть» и смотри, что автор прятал"
            isOwner -> "Это видишь только ты. Пост обернётся через ${formatDuration(millisToMidnight())}, тогда его увидят все"
            else -> null
        }
        hint?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (help) WerewolfHelpDialog(isOwner) { help = false }
}

/** Night text and images shown in place of the day version. */
@Composable
fun WerewolfNightContent(werewolf: HalloweenWerewolf) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(NightBackground).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        werewolf.text?.let { Text(it, color = Color.White, fontSize = 15.sp) }
        werewolf.images.forEach { url ->
            AsyncImage(url, "Пост-оборотень: ночная версия поста", Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)))
        }
    }
}

@Composable
fun WerewolfHelpDialog(isOwner: Boolean, onDismiss: () -> Unit) {
    val pages = listOf(
        "Две версии одного поста" to if (isOwner) {
            "Напиши обычный пост, затем нажми луну и добавь ночную версию. В ней могут быть свой текст и картинки."
        } else {
            "У этого поста есть обычная и ночная версии. До полуночи остальные видят только обычную."
        },
        "В полночь переворачивается" to "С 00:00 до 9 утра все видят ночную версию. Потом её можно глянуть и днём, кнопкой «Перевернуть».",
        "Ночные конфеты за лайки" to "Лайкни чужого оборотня ночью и лови ночную конфету. До трёх за ночь, за свой пост не дают.",
    )
    var page by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pages[page].first) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page == 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Ну и погодка сегодня, конечно",
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                            fontSize = 12.sp,
                        )
                        Text(
                            "А ночью я оборотень, р-р-р",
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(NightBackground).padding(8.dp),
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (page == 2) AsyncImage(HalloweenArt.url("wolf-fang"), null, Modifier.size(56.dp))
                Text(pages[page].second)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (page < pages.lastIndex) page++ else onDismiss() }) {
                Text(if (page < pages.lastIndex) "Дальше" else "Понятно")
            }
        },
    )
}

/** Event status line above the feed: night, full moon or time left until midnight. */
@Composable
fun HalloweenFeedBanner(modifier: Modifier = Modifier) {
    val portal by HalloweenPortalManager.state.collectAsState()
    val night by HalloweenFeed.night.collectAsState()
    val fullMoon by HalloweenNotices.fullMoon.collectAsState()
    if (!portal.actionsAvailable) return
    val text = when {
        night -> "🌙 Сейчас: ночь (будет ещё до 9:00). Лайкай посты-оборотни с ушками и получай ночные конфеты!"
        fullMoon -> "🌕 Полнолуние. Призраков вдвое больше. Итоги в полночь"
        else -> "🎃 До полуночи ${formatDuration(millisToMidnight())}. Изгоняй призраков в ленте и собирай конфеты"
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
            .background(if (night) NightBackground else MaterialTheme.colorScheme.surface)
            .clickable { HalloweenNavigation.openEventSection("finale") }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = if (night) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
    }
}
