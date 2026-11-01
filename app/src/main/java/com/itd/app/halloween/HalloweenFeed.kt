package com.itd.app.halloween

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlin.math.abs
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

/**
 * Keeps Halloween haunts (possessions and werewolves) for posts currently on screen:
 * refreshed every 20 s and shortly after new posts appear, paused while the user holds a ghost.
 */
object HalloweenFeed {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _haunts = MutableStateFlow<Map<String, HalloweenPostHaunt>>(emptyMap())
    val haunts: StateFlow<Map<String, HalloweenPostHaunt>> = _haunts.asStateFlow()
    private val _night = MutableStateFlow(false)
    val night: StateFlow<Boolean> = _night.asStateFlow()

    /** Server clock minus device clock, measured from feed-state round trips. */
    @Volatile
    var clockOffset = 0L
        private set
    private val holding = HashSet<String>()
    private val tracked = HashMap<String, Int>()
    private val recent = ArrayList<String>()
    private val locallyChanged = HashMap<String, Long>()
    var repository: HalloweenRepository? = null
    var enabled = false
    var foreground = true
    private var inFlight = false
    private var refreshPending = false
    private var refreshJob: Job? = null
    var pollJob: Job? = null

    fun now(): Long = System.currentTimeMillis() + clockOffset

    /** Once per night: "the werewolves have turned" toast. */
    private fun announceMidnight() {
        val user = HalloweenNotices.userId ?: return
        val day = java.time.LocalDate.ofEpochDay(Math.floorDiv(now() + 10_800_000, 86_400_000L))
        val key = "halloween-midnight:$user:" + String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", day.year, day.monthValue, day.dayOfMonth)
        if (HalloweenFlags.get(key)) return
        HalloweenFlags.set(key)
        HalloweenToasts.show("Полночь. Посты-оборотни перевернулись", "Видишь пост с ушками? Лайкни его и получишь ночную конфету. До 9 утра", "moon")
    }

    fun haunt(postId: String): HalloweenPostHaunt? = _haunts.value[postId]

    /** Applies a local change to a possession (after hold/thanks) so the next poll does not overwrite it. */
    fun updatePossession(postId: String, transform: (HalloweenPossession) -> HalloweenPossession) {
        val current = _haunts.value[postId] ?: return
        val possession = current.possession ?: return
        locallyChanged[postId] = System.currentTimeMillis()
        _haunts.value = _haunts.value + (postId to HalloweenPostHaunt(transform(possession), current.werewolf))
    }

    fun setHolding(postId: String, holding: Boolean) {
        val wasEmpty = this.holding.isEmpty()
        if (holding) this.holding.add(postId) else this.holding.remove(postId)
        if (!wasEmpty && this.holding.isEmpty()) scheduleRefresh()
    }

    fun track(postId: String) {
        tracked[postId] = (tracked[postId] ?: 0) + 1
        scheduleRefresh()
        recent.remove(postId)
        recent.add(0, postId)
    }

    fun untrack(postId: String) {
        recent.remove(postId)
        val count = (tracked[postId] ?: 1) - 1
        if (count > 0) tracked[postId] = count else tracked.remove(postId)
    }

    fun start(repository: HalloweenRepository) {
        this.repository = repository
        if (enabled) return
        enabled = true
        if (foreground) startPolling()
        scheduleRefresh()
    }

    fun setForegroundState(foreground: Boolean) {
        if (this.foreground == foreground) return
        this.foreground = foreground
        if (!foreground) {
            pollJob?.cancel()
            pollJob = null
            refreshJob?.cancel()
            refreshJob = null
        } else if (enabled) {
            startPolling()
            scheduleRefresh()
        }
    }

    fun stop() {
        enabled = false
        pollJob?.cancel()
        pollJob = null
        refreshJob?.cancel()
        refreshJob = null
        _haunts.value = emptyMap()
        _night.value = false
        clockOffset = 0L
        locallyChanged.clear()
        holding.clear()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(20_000)
                refresh()
            }
        }
    }

    private fun scheduleRefresh() {
        if (!enabled || !foreground) return
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(300)
            refresh()
        }
    }

    private suspend fun refresh() {
        val repository = repository ?: return
        if (!enabled || holding.isNotEmpty()) return
        if (inFlight) {
            refreshPending = true
            return
        }
        val ids = LinkedHashSet<String>()
        recent.filterTo(ids) { tracked.containsKey(it) }
        for (id in tracked.keys) if (ids.size < 50) ids.add(id)
        val batch = ids.take(50)
        if (batch.isEmpty()) return
        inFlight = true
        try {
            val requestedAt = System.currentTimeMillis()
            val state = try {
                repository.feedState(batch)
            } catch (_: Exception) {
                null
            } ?: return
            if (!enabled) return
            val offset = state.serverNow - ((System.currentTimeMillis() - requestedAt) / 2 + requestedAt)
            if (abs(offset - clockOffset) >= 1_000) clockOffset = offset
            if (_night.value != state.night) {
                if (state.night && !_night.value) announceMidnight()
                _night.value = state.night
            }
            val current = _haunts.value
            var updated: HashMap<String, HalloweenPostHaunt>? = null
            for (id in batch) {
                val changedAt = locallyChanged[id]
                if (changedAt != null && changedAt > requestedAt) continue
                val haunt = state.posts[id]
                if (current[id] != haunt) {
                    val map = updated ?: HashMap(current).also { updated = it }
                    if (haunt == null) map.remove(id) else map[id] = haunt
                }
            }
            updated?.let { _haunts.value = it }
        } finally {
            inFlight = false
            if (refreshPending && enabled) {
                refreshPending = false
                scheduleRefresh()
            }
        }
    }
}

/** Registers [postId] for haunt polling while the composable is on screen and returns its current haunt. */
@Composable
fun rememberHalloweenHaunt(postId: String): State<HalloweenPostHaunt?> {
    DisposableEffect(postId) {
        HalloweenFeed.track(postId)
        onDispose { HalloweenFeed.untrack(postId) }
    }
    val haunts = HalloweenFeed.haunts.collectAsState()
    return remember(postId) { derivedStateOf { haunts.value[postId] } }
}
