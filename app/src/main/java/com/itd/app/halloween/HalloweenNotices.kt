package com.itd.app.halloween

import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HalloweenThanks(val from: List<String>, val level: Int?)

/** Sound effect associated with each in-app notification type. */
val HalloweenNotificationSounds = mapOf(
    "like" to HalloweenSound.WINGS,
    "post_reaction" to HalloweenSound.WINGS,
    "comment_like" to HalloweenSound.WINGS,
    "comment_reaction" to HalloweenSound.WINGS,
    "comment" to HalloweenSound.FEATHER,
    "post_comment" to HalloweenSound.FEATHER,
    "reply" to HalloweenSound.FEATHER,
    "comment_reply" to HalloweenSound.FEATHER,
    "follow" to HalloweenSound.DOOR,
)

/** Per-user event lifecycle: settings, the sounds prompt, full-moon notice and server notices (polled every minute). */
object HalloweenNotices {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var repository: HalloweenRepository? = null
    var userId: String? = null
        private set
    var username: String? = null
        private set
    private var enabled = false
    private var epoch = 0
    private var foreground = true
    private var pollJob: Job? = null
    private var settingsJob: Job? = null
    private val shownNotices = HashSet<String>()
    private var polling = false
    private var generation = 0
    private var soundsPromptId: Int? = null

    private val _thanks = MutableStateFlow<HalloweenThanks?>(null)
    val thanks: StateFlow<HalloweenThanks?> = _thanks.asStateFlow()
    private val _fullMoon = MutableStateFlow(false)
    val fullMoon: StateFlow<Boolean> = _fullMoon.asStateFlow()

    fun dismissThanks() {
        _thanks.value = null
    }

    /** Called whenever the signed-in user, event availability or lifecycle epoch changes. */
    fun start(userId: String?, username: String?, enabled: Boolean, epoch: Int, repository: HalloweenRepository, onEnabled: () -> Unit) {
        this.username = username
        if (userId == this.userId && enabled == this.enabled && epoch == this.epoch) return
        pollJob?.cancel()
        pollJob = null
        settingsJob?.cancel()
        settingsJob = null
        shownNotices.clear()
        generation++
        soundsPromptId?.let(HalloweenToasts::dismiss)
        soundsPromptId = null
        HalloweenFeed.stop()
        HalloweenSounds.setEnabled(false)
        _thanks.value = null
        _fullMoon.value = false
        HalloweenToasts.clear()
        polling = false
        this.userId = userId
        this.enabled = enabled
        this.epoch = epoch
        this.repository = repository
        if (userId == null || !enabled) return
        onEnabled()
        HalloweenFeed.foreground = foreground
        HalloweenFeed.start(repository)
        if (foreground) startPolling()
        scope.launch { loadSettings(userId, silent = true) }
        settingsJob = scope.launch { loadSettings(userId, silent = false) }
    }

    fun setForeground(foreground: Boolean) {
        if (this.foreground == foreground) return
        this.foreground = foreground
        HalloweenFeed.setForegroundState(foreground)
        if (!foreground) {
            pollJob?.cancel()
            pollJob = null
        } else if (enabled) {
            startPolling()
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                pollNotices()
                delay(60_000)
            }
        }
    }

    /** Remembers the answer to the sounds prompt and stores it on the server. */
    fun answerSounds(userId: String, key: String, enable: Boolean) {
        if (this.userId != userId) return
        HalloweenFlags.set(key)
        scope.launch {
            try {
                repository?.updateSettings(enable)
                if (this@HalloweenNotices.userId == userId) HalloweenSounds.setEnabled(enable)
            } catch (_: Exception) {
            }
            soundsPromptId?.let(HalloweenToasts::dismiss)
            soundsPromptId = null
        }
    }

    fun thank(possessionId: String) {
        val repository = repository ?: return
        scope.launch {
            try {
                repository.thank(possessionId)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun loadSettings(userId: String, silent: Boolean) {
        val request = ++generation
        val me = try {
            repository?.me()
        } catch (_: Exception) {
            null
        } ?: return
        if (request != generation || userId != this.userId || !enabled) return
        val fullMoon = me.eventDay == 4
        _fullMoon.value = fullMoon
        val day = LocalDate.ofEpochDay(Math.floorDiv(HalloweenFeed.now() + 10_800_000, 86_400_000L))
        val moonKey = "halloween-moon:$userId:" + String.format(Locale.ROOT, "%04d-%02d-%02d", day.year, day.monthValue, day.dayOfMonth)
        if (fullMoon && !HalloweenFlags.get(moonKey)) {
            HalloweenFlags.set(moonKey)
            HalloweenToasts.show(
                "ПОЛНОЛУНИЕ",
                "Призраков вдвое больше. В полночь подведём итоги",
                "moon",
                listOf(
                    HalloweenToastAction("Как идёт мой Хеллоуин") { HalloweenNavigation.openEventSection("finale") },
                    HalloweenToastAction("Позже") {},
                ),
            )
        }
        HalloweenSounds.setEnabled(me.sounds)
        val askedKey = "halloween-sounds-asked:$userId"
        if (!silent && !me.sounds && !me.soundsAsked && !HalloweenFlags.get(askedKey)) {
            soundsPromptId = HalloweenToasts.show(
                "В ИТД завелась нечисть. Включить звуки?",
                art = "ghost-orig",
                actions = listOf(
                    HalloweenToastAction("Включить") { answerSounds(userId, askedKey, true) },
                    HalloweenToastAction("Не сейчас") { answerSounds(userId, askedKey, false) },
                ),
                key = askedKey,
                onDismiss = { if (this.userId == userId) HalloweenFlags.set(askedKey) },
            )
        } else if (me.sounds || me.soundsAsked || HalloweenFlags.get(askedKey)) {
            soundsPromptId?.let(HalloweenToasts::dismiss)
            soundsPromptId = null
        }
    }

    private suspend fun pollNotices() {
        val userId = userId ?: return
        val repository = repository ?: return
        if (!enabled || polling) return
        polling = true
        try {
            val notices = try {
                repository.notices()
            } catch (_: Exception) {
                null
            } ?: return
            if (userId != this.userId || !enabled) return
            val unread = notices.filter { it.readAt == null && it.id !in shownNotices }
            val gratitude = unread.filter { it.kind == "thanks" || it.kind == "lantern" }
            if (gratitude.isNotEmpty()) {
                val level = gratitude.filter { it.kind == "lantern" }.mapNotNull { HalloweenJson.run { it.payload["level"].int() } }.maxOrNull()
                val from = LinkedHashSet<String>()
                gratitude.filter { it.kind == "thanks" }.forEach { notice ->
                    HalloweenJson.run { notice.payload["from"].field("username").string() }?.let(from::add)
                }
                _thanks.value = HalloweenThanks(from.toList(), level)
            }
            val others = unread.filter { it !in gratitude }.take(3)
            for (notice in others) {
                val content = HalloweenNoticeTexts.toast(notice, username) ?: continue
                HalloweenToasts.show(content.title, content.sub, content.art, content.actions, key = "halloween-notice:$userId:${notice.id}")
            }
            val handled = gratitude + others
            handled.forEach { shownNotices.add(it.id) }
            if (handled.isNotEmpty()) {
                try {
                    repository.markNoticesRead(ArrayList(handled.map { it.id }))
                } catch (_: Exception) {
                }
            }
        } finally {
            polling = false
        }
    }
}
