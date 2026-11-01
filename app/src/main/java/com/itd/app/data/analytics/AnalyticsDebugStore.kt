package com.itd.app.data.analytics

import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Keeps the last five dwell events, interactions and batches for the debug panel. */
class AnalyticsDebugStore {
    private val _dwells = MutableStateFlow<List<DwellLogEntry>>(emptyList())
    val dwells: StateFlow<List<DwellLogEntry>> = _dwells
    private val _interactions = MutableStateFlow<List<InteractionLogEntry>>(emptyList())
    val interactions: StateFlow<List<InteractionLogEntry>> = _interactions
    private val _batches = MutableStateFlow<List<BatchSnapshot>>(emptyList())
    val batches: StateFlow<List<BatchSnapshot>> = _batches

    private val dwellQueue = ArrayDeque<DwellLogEntry>()
    private val interactionQueue = ArrayDeque<InteractionLogEntry>()
    private val batchQueue = ArrayDeque<BatchSnapshot>()

    fun recordDwell(entry: DwellLogEntry) = push(dwellQueue, entry, _dwells)

    fun recordInteraction(entry: InteractionLogEntry) = push(interactionQueue, entry, _interactions)

    fun recordBatch(snapshot: BatchSnapshot) = push(batchQueue, snapshot, _batches)

    private fun <T> push(queue: ArrayDeque<T>, value: T, flow: MutableStateFlow<List<T>>) {
        synchronized(queue) {
            if (queue.size >= 5) queue.pollLast()
            queue.addFirst(value)
            flow.value = queue.toList()
        }
    }
}
