package com.itd.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.itd.app.BuildConfig
import com.itd.app.di.AppContainer
import com.itd.app.util.Diagnostics
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val snapshotTime = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss").withZone(ZoneId.systemDefault())

private fun networkType(context: Context): String {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "offline"
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "wired"
        else -> "other"
    }
}

fun diagnosticsReport(context: Context, container: AppContainer): String {
    val user = container.authRepository.authState.value?.user
    val log = Diagnostics.recentLog()
    val net = Diagnostics.recentNetwork()
    return buildString {
        appendLine("ITD-DIAG v1")
        appendLine("platform: android ${Build.VERSION.RELEASE}")
        appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("build: ${if (BuildConfig.DEBUG) "debug" else "release"}")
        appendLine("commit: ${BuildConfig.GIT_COMMIT}")
        appendLine("branch: ${BuildConfig.GIT_BRANCH}")
        appendLine("built: ${BuildConfig.BUILD_TIME}")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("locale: ${Locale.getDefault().toLanguageTag().ifEmpty { "unknown" }}")
        appendLine("network: ${networkType(context)}")
        appendLine("session: ${Diagnostics.processId}")
        appendLine("device_id: ${container.tokenStore.deviceId}")
        appendLine("user: ${user?.username?.let { "@$it" } ?: "anonymous"}")
        appendLine("captured: ${Instant.now()}")
        appendLine()
        appendLine(if (log.isEmpty()) "--- LOG (last 200) ---\n(пусто)" else "--- LOG (last 200) ---")
        log.forEach(::appendLine)
        appendLine()
        appendLine(if (net.isEmpty()) "--- NET (last 20) ---\n(пусто)" else "--- NET (last 20) ---")
        net.forEach(::appendLine)
    }
}

@Composable
fun DiagnosticsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val snapshots = remember { mutableStateListOf<Pair<Instant, String>>() }
    var report by remember { mutableStateOf(diagnosticsReport(context, container)) }
    SettingsPage("Диагностика", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("ITD diagnostics", report))
                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
            }) { Text("Скопировать") }
            OutlinedButton(onClick = { shareReport(context, report) }) { Text("Поделиться") }
            OutlinedButton(onClick = {
                report = diagnosticsReport(context, container)
                snapshots.add(0, Instant.now() to report)
            }) { Text("Снимки") }
        }
        snapshots.forEach { (time, text) ->
            Text(
                snapshotTime.format(time),
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { report = text }.padding(12.dp),
                fontSize = 13.sp,
            )
        }
        Text(
            report,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState()).padding(12.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

private fun shareReport(context: Context, report: String) {
    val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
    val file = File(directory, "itd-diag-${System.currentTimeMillis()}.txt").apply { writeText(report) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Диагностика"))
}
