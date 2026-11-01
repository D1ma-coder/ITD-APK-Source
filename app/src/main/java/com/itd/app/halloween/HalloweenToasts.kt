package com.itd.app.halloween

import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HalloweenToastAction(val label: String, val onClick: () -> Unit)

data class HalloweenToast(
    val id: Int,
    val title: String,
    val sub: String?,
    val art: String?,
    val actions: List<HalloweenToastAction>,
    val key: String?,
) {
    var onDismiss: (() -> Unit)? = null
}

/** Event toasts shown over the app; at most three at a time, auto-dismissed unless they carry actions. */
object HalloweenToasts {
    private class Timer(var remaining: Long, var startedAt: Long, var job: Job?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _toasts = MutableStateFlow<List<HalloweenToast>>(emptyList())
    val toasts: StateFlow<List<HalloweenToast>> = _toasts.asStateFlow()
    private var lastId = 0
    private val timers = HashMap<Int, Timer>()

    fun dismiss(id: Int) {
        timers.remove(id)?.job?.cancel()
        _toasts.value = _toasts.value.filter { it.id != id }
    }

    /** Pauses the auto-dismiss timer while the toast is touched and resumes it (at least 2 s) afterwards. */
    fun setPaused(id: Int, paused: Boolean) {
        val timer = timers[id] ?: return
        if (paused == (timer.job == null)) return
        timer.job?.cancel()
        if (!paused) {
            schedule(id, max(timer.remaining, 2_000L))
            return
        }
        val now = System.currentTimeMillis()
        timer.remaining -= now - timer.startedAt
        timer.startedAt = now
        timer.job = null
    }

    private fun schedule(id: Int, delayMs: Long) {
        val job = scope.launch {
            delay(delayMs)
            dismiss(id)
        }
        timers[id] = Timer(delayMs, System.currentTimeMillis(), job)
    }

    fun show(
        title: String,
        sub: String? = null,
        art: String? = null,
        actions: List<HalloweenToastAction> = emptyList(),
        durationMs: Long? = null,
        key: String? = null,
        onDismiss: (() -> Unit)? = null,
    ): Int {
        if (key != null) _toasts.value.firstOrNull { it.key == key }?.let { return it.id }
        val id = ++lastId
        _toasts.value.dropLast(2).forEach { dismiss(it.id) }
        val toast = HalloweenToast(id, title, sub, art, actions, key).also { it.onDismiss = onDismiss }
        _toasts.value = _toasts.value + toast
        val duration = durationMs ?: when {
            actions.isNotEmpty() -> 0L
            sub == null && art == null -> 3_000L
            else -> 10_000L
        }
        if (duration > 0) schedule(id, duration)
        return id
    }

    fun clear() {
        _toasts.value.forEach { dismiss(it.id) }
    }
}
