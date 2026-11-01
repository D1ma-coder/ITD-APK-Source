package com.itd.app.halloween

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GhostBackground = Color(0xFF1E1B2E)
private val exorcismPhrases = listOf(
    "Соль и огонь. Винчестеры бы одобрили",
    "Святая вода...",
    "Ловушка захлопнулась. Призраку пора на выход",
    "Exorcizamus te. Изыди, и без обид",
)
private const val HOLD_MS = 1_200L

private fun saverNames(possession: HalloweenPossession, myUsername: String?): String {
    val names = possession.savers.orEmpty().map { saver ->
        if (saver.username != null && saver.username == myUsername) "ты" else saver.username?.let { "@$it" } ?: "кто-то"
    }
    return when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " и " + names.last()
    }
}

/** Ghost state of a post with the hold-to-exorcise interaction. */
@Composable
fun PossessionPanel(postId: String, authorUsername: String?, possession: HalloweenPossession, isOwner: Boolean) {
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var holding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var thanking by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (holding) 1f else 0f, tween(if (holding) HOLD_MS.toInt() else 150), label = "hold")
    val repository = HalloweenFeed.repository
    val me = HalloweenNotices.username
    val now = HalloweenFeed.now()

    fun share() = HalloweenNavigation.sharePost(authorUsername.orEmpty(), postId, "Мой пост захватил призрак, помоги освободить его фонарём")

    fun hold() {
        val repo = repository ?: return
        busy = true
        HalloweenFeed.setHolding(postId, true)
        scope.launch {
            try {
                val result = repo.hold(possession.id)
                if (result == null) {
                    HalloweenToasts.show("Не получилось изгнать. Попробуй ещё раз")
                    return@launch
                }
                HalloweenFeed.updatePossession(postId) {
                    it.copy(holders = result.holders, threshold = result.threshold, heldByMe = true, resolved = result.resolved ?: it.resolved)
                }
                if (result.resolved != null) {
                    HalloweenSounds.play(HalloweenSound.DOOR, forced = true)
                    val firstKey = "halloween-first-exorcism:${HalloweenNotices.userId}"
                    val title = if (!HalloweenFlags.get(firstKey)) {
                        HalloweenFlags.set(firstKey)
                        "Первый призрак изгнан!"
                    } else {
                        exorcismPhrases.random()
                    }
                    val candy = result.candy
                    if (candy != null) {
                        HalloweenToasts.show(
                            title,
                            "Конфета уже летит в банку. Изгоняй призраков в ленте и собирай конфеты",
                            candy.kind,
                            listOf(HalloweenToastAction("Открыть банку") { HalloweenNavigation.openEventSection("jar") }),
                        )
                    } else {
                        HalloweenToasts.show(title, "Изгоняй призраков в ленте и собирай конфеты", "puff")
                    }
                } else {
                    HalloweenToasts.show(
                        "Ты ослабил призрака",
                        "Позови друзей на помощь. Когда призрак появится снова, нажмите на него и удерживайте нажатие. После изгнания участники получат конфеты",
                        "ghost-orig",
                        listOf(HalloweenToastAction("Позвать друзей") { share() }),
                    )
                }
            } catch (_: Exception) {
                HalloweenToasts.show("Не получилось изгнать. Попробуй ещё раз")
            } finally {
                HalloweenFeed.setHolding(postId, false)
                busy = false
            }
        }
    }

    val resolved = possession.resolved
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(GhostBackground).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (resolved != null) {
            Text(if (resolved == HalloweenResolution.FREED) "Пост освобождён" else "Призрак изгнан", color = Color.White, fontWeight = FontWeight.SemiBold)
            val names = saverNames(possession, me)
            val left = possession.stampUntil?.let { ((it - now) / 60_000).coerceAtLeast(0) }
            if (names.isEmpty()) {
                Text("Имена спасителей видны под постом в течение часа", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            } else {
                Text(
                    names + (left?.let { ", имена спасителей видны ещё $it мин" } ?: ""),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                )
            }
            if (possession.canThank && isOwner) {
                Text(
                    "Сказать спасибо",
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable(enabled = !thanking) {
                        thanking = true
                        scope.launch {
                            try {
                                repository?.thank(possession.id)
                                HalloweenFeed.updatePossession(postId) { it.copy(canThank = false) }
                                HalloweenToasts.show("Спасибо отправлено", "Фонарь спасителей стал чуть ярче", "lantern")
                            } catch (_: Exception) {
                                HalloweenToasts.show("Спасибо не отправилось", "Проверь интернет и нажми «Повторить»")
                            } finally {
                                thanking = false
                            }
                        }
                    }.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Пост захвачен", color = Color.White, fontWeight = FontWeight.SemiBold)
                if (possession.weakness > 0) Text("призрак ослаб на ${possession.weakness}%", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Text(
                    when {
                        isOwner -> "Ты не можешь спасти свой пост сам. Зови друзей на помощь"
                        possession.holders.isEmpty() -> "Пост захвачен. Жми «Освободить» и лови призрака фонарём"
                        possession.holders.size == 1 && possession.threshold > 1 -> "Одному не справиться. " + HalloweenNoticeTexts.holdersNeeded(1, possession.threshold)
                        else -> HalloweenNoticeTexts.holdersNeeded(possession.holders.size, possession.threshold)
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                )
            }
            AsyncImage(
                HalloweenArt.url(if (holding) "g-face-strain" else "ghost-orig"),
                "Призрак",
                Modifier.size(64.dp).scale(1f - progress * 0.25f).let { modifier ->
                    if (isOwner || busy) modifier else modifier.pointerInput(possession.id) {
                        detectTapGestures(onPress = {
                            holding = true
                            holdJob = scope.launch {
                                delay(HOLD_MS)
                                hold()
                            }
                            val released = tryAwaitRelease()
                            holding = false
                            if (released && holdJob?.isCompleted == false) {
                                holdJob?.cancel()
                                HalloweenToasts.show("Призрак всё ещё здесь", "Нажми на призрака и удерживай нажатие. Когда соберётся команда, он исчезнет")
                            }
                        })
                    }
                },
            )
        }
        if (holding) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Color(0xFFFFB74D))
        if (possession.holders.size > 1) {
            Text("Изгоняют: ${possession.holders.size} из ${possession.threshold}", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isOwner) {
                Text(
                    "Освободить",
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFB74D)).padding(horizontal = 14.dp, vertical = 6.dp),
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Позвать друзей",
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { share() }.padding(horizontal = 14.dp, vertical = 6.dp),
                color = Color.White,
                fontSize = 13.sp,
            )
        }
    }
}

/** First-launch announcement of the event. */
@Composable
fun HalloweenIntroDialog() {
    val userId = HalloweenNotices.userId ?: return
    val key = "halloween-intro:$userId"
    var visible by remember(userId) { mutableStateOf(!HalloweenFlags.get(key)) }
    if (!visible) return
    fun close() {
        HalloweenFlags.set(key)
        visible = false
    }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text("ААА!! ВНИМАНИЕ!! В ИТД проникли тёмные силы!!") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { AsyncImage(HalloweenArt.url("ghost"), null, Modifier.size(96.dp)) }
                Text("Будь аккуратен, с сегодняшней ночи в посты вселяются призраки! Ты должен дать им отпор! Лови привидений, помогай друзьям и получай за это конфеты!")
            }
        },
        confirmButton = { TextButton(onClick = ::close) { Text("Понятно") } },
        dismissButton = {
            TextButton(onClick = {
                close()
                HalloweenNavigation.openEventSection(null)
            }) { Text("Узнать больше") }
        },
    )
}
