package com.itd.app.util

import android.util.Log
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import timber.log.Timber

/** In-memory ring buffers of recent log lines and network calls, shared in diagnostics reports. */
object Diagnostics {
    /** Random id of this app process, so reports from the same run can be matched. */
    val processId: String = (1..6).map { "0123456789ABCDEF"[kotlin.random.Random.nextInt(16)] }.joinToString("")

    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneOffset.UTC)
    private val lock = Any()
    private val logLines = arrayOfNulls<String>(200)
    private var logNext = 0
    private var logCount = 0
    private val netLines = arrayOfNulls<String>(20)
    private var netNext = 0
    private var netCount = 0

    fun log(priority: Int, tag: String?, message: String) {
        val level = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "A"
        }
        val line = "${time.format(Instant.now())} $level/${tag ?: "-"}: $message"
        synchronized(lock) {
            logLines[logNext] = line
            logNext = (logNext + 1) % logLines.size
            logCount = minOf(logCount + 1, logLines.size)
        }
    }

    fun network(method: String, url: String, status: Int?, durationMs: Long, bytes: Long) {
        val line = "${time.format(Instant.now())} $method ${redactPath(url)} ${status ?: "ERR"} ${durationMs}ms ${formatSize(bytes)}"
        synchronized(lock) {
            netLines[netNext] = line
            netNext = (netNext + 1) % netLines.size
            netCount = minOf(netCount + 1, netLines.size)
        }
    }

    fun recentLog(): List<String> = synchronized(lock) { ordered(logLines, logNext, logCount) }

    fun recentNetwork(): List<String> = synchronized(lock) { ordered(netLines, netNext, netCount) }

    private fun ordered(buffer: Array<String?>, next: Int, count: Int): List<String> =
        (0 until count).mapNotNull { buffer[(next - count + it + buffer.size) % buffer.size] }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1_048_576 -> String.format(Locale.US, "%.1fKB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1fMB", bytes / 1_048_576.0)
    }

    /** Path of [url] without host, query or fragment; identifiers are replaced with ":id". */
    fun redactPath(url: String?): String {
        if (url.isNullOrEmpty()) return "?"
        val path = url.substringAfter("://", url).substringAfter('/', "").substringBefore('?').substringBefore('#')
        if (path.isEmpty()) return "/"
        return "/" + path.split('/').filter { it.isNotEmpty() }.joinToString("/") { segment ->
            if (segment.length >= 16 && segment.any(Char::isDigit) || segment.all(Char::isDigit)) ":id" else segment
        }
    }
}

/** Timber tree feeding [Diagnostics]. */
class DiagnosticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Diagnostics.log(priority, tag, if (t != null) "$message ${t.javaClass.simpleName}: ${t.message}" else message)
    }
}
