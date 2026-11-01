package com.itd.app.halloween

import com.itd.app.data.model.PortalInfo
import com.itd.app.halloween.HalloweenJson.bool
import com.itd.app.halloween.HalloweenJson.epochMillis
import com.itd.app.halloween.HalloweenJson.field
import com.itd.app.halloween.HalloweenJson.int
import com.itd.app.halloween.HalloweenJson.obj
import com.itd.app.halloween.HalloweenJson.string
import com.itd.app.util.LogTag
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

enum class HalloweenPhase { SCHEDULED, ACTIVE, CLOSED }

data class HalloweenEvent(
    val eventId: String,
    val phase: HalloweenPhase,
    val visible: Boolean,
    val actionsEnabled: Boolean,
    val startsAt: Long,
    val endsAt: Long,
    val displayUntil: Long,
    val lifecycleEpoch: Int,
    val capabilities: Map<String, Boolean>,
)

data class HalloweenPortal(
    val active: Boolean,
    val serverTime: Long,
    val revision: String,
    val event: HalloweenEvent?,
) {
    companion object {
        private val EVENT_ID = Regex("^[a-z0-9][a-z0-9_-]{0,99}$")
        private val REVISION = Regex("^[a-f0-9]{16}$")

        fun parse(root: JsonElement?): HalloweenPortal? {
            val active = root.field("active").bool() ?: return null
            val serverTime = epochMillis(root.field("serverTime").string()) ?: return null
            val revision = root.field("revision").string()?.takeIf(REVISION::matches) ?: return null
            val eventElement = root.field("event")
            if ((eventElement == null || eventElement is JsonNull) && !active) return HalloweenPortal(false, serverTime, revision, null)
            val event = eventElement.obj() ?: return null
            if (event["slug"].string() != "halloween" || event["route"].string() != "/event/halloween") return null
            if (event["framePath"].string()?.trimEnd('/') != "/public/events/halloween") return null
            val eventId = event["eventId"].string()?.takeIf(EVENT_ID::matches) ?: return null
            val visible = event["visible"].bool()?.takeIf { it == active } ?: return null
            val actionsEnabled = event["actionsEnabled"].bool() ?: return null
            val phase = when (event["phase"].string()) {
                "scheduled" -> HalloweenPhase.SCHEDULED
                "active" -> HalloweenPhase.ACTIVE
                "closed" -> HalloweenPhase.CLOSED
                else -> return null
            }
            val epoch = event["lifecycleEpoch"].int()?.takeIf { it >= 1 } ?: return null
            val startsAt = epochMillis(event["startsAt"].string()) ?: return null
            val endsAt = epochMillis(event["endsAt"].string()) ?: return null
            val displayUntil = epochMillis(event["displayUntil"].string()) ?: return null
            if (startsAt >= endsAt || endsAt > displayUntil) return null
            val capabilities = event["capabilities"].obj() ?: return null
            val flags = HashMap<String, Boolean>(capabilities.size)
            for ((key, value) in capabilities) value.bool()?.let { flags[key] = it }
            return HalloweenPortal(active, serverTime, revision, HalloweenEvent(eventId, phase, visible, actionsEnabled, startsAt, endsAt, displayUntil, epoch, flags))
        }
    }
}

/** Polls the portal endpoint for the signed-in user and exposes the current Halloween event. */
object HalloweenPortalManager {
    data class State(
        val portal: PortalInfo? = null,
        val event: HalloweenEvent? = null,
        val loaded: Boolean = false,
        val failed: Boolean = false,
    ) {
        val actionsAvailable: Boolean
            get() = event != null && event.visible && event.phase == HalloweenPhase.ACTIVE && event.actionsEnabled
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Server clock minus device clock. */
    @Volatile
    var clockOffset: Long = 0L
        private set
    private var repository: HalloweenRepository? = null
    private var userId: String? = null
    private var foreground = true
    private var pollJob: Job? = null
    private var phaseJob: Job? = null
    private val mutex = Mutex()
    private var lastServerTime = Long.MIN_VALUE

    val actionsAvailable: Boolean
        get() = _state.value.actionsAvailable

    fun start(userId: String, repository: HalloweenRepository) {
        if (this.userId == userId && pollJob != null) return
        reset()
        this.userId = userId
        this.repository = repository
        if (foreground) {
            pollJob?.cancel()
            pollJob = scope.launch { pollLoop() }
        }
    }

    fun setForeground(foreground: Boolean) {
        if (this.foreground == foreground) return
        this.foreground = foreground
        if (!foreground) {
            pollJob?.cancel()
            pollJob = null
            return
        }
        if (userId == null) return
        updatePhase()
        pollJob?.cancel()
        pollJob = scope.launch { pollLoop() }
    }

    fun reset() {
        pollJob?.cancel()
        pollJob = null
        phaseJob?.cancel()
        phaseJob = null
        userId = null
        lastServerTime = Long.MIN_VALUE
        clockOffset = 0L
        _state.value = State()
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            delay(15_000)
        }
    }

    suspend fun refresh() {
        val repository = repository ?: return
        if (mutex.isLocked) return
        mutex.lock()
        try {
            val requestedFor = userId ?: return
            val root = try {
                repository.portal()
            } catch (e: Exception) {
                if (requestedFor == userId) _state.update { it.copy(failed = true) }
                LogTag.APP.tree().d("portal refresh failed: %s", e.message)
                return
            }
            if (requestedFor != userId) return
            val portalInfo = root?.let { element ->
                element.field("active").bool()?.let { active ->
                    PortalInfo(active, element.field("url").string() ?: "", element.field("title").string() ?: "Портал")
                }
            }
            val portal = HalloweenPortal.parse(root)
            when {
                portal != null -> if (portal.serverTime >= lastServerTime) {
                    lastServerTime = portal.serverTime
                    val offset = portal.serverTime - System.currentTimeMillis()
                    if (abs(offset - clockOffset) >= 1_000) clockOffset = offset
                    _state.value = State(portalInfo, portal.event, loaded = true, failed = false)
                    updatePhase()
                }
                portalInfo != null -> _state.value = State(portalInfo, null, loaded = true, failed = false)
                else -> _state.update { it.copy(failed = true) }
            }
        } finally {
            mutex.unlock()
        }
    }

    /** Moves the event to CLOSED locally when its deadlines pass and schedules the next transition. */
    private fun updatePhase() {
        phaseJob?.cancel()
        val event = _state.value.event ?: return
        if (!event.visible) return
        val now = System.currentTimeMillis() + clockOffset
        val updated = when {
            event.displayUntil <= now -> event.copy(phase = HalloweenPhase.CLOSED, visible = false)
            event.phase == HalloweenPhase.ACTIVE && event.endsAt <= now ->
                event.copy(phase = HalloweenPhase.CLOSED, capabilities = event.capabilities + ("pumpkins" to false))
            else -> event
        }
        if (updated != event) _state.update { it.copy(event = updated) }
        if (!updated.visible) return
        val next = listOfNotNull(if (updated.phase == HalloweenPhase.ACTIVE) updated.endsAt else null, updated.displayUntil)
            .filter { it > now }
            .minOrNull() ?: return
        phaseJob = scope.launch {
            delay(max(1L, next - now))
            updatePhase()
        }
    }
}
