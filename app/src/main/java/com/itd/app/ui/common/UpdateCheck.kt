package com.itd.app.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.BuildConfig
import com.itd.app.data.model.PlatformVersion
import com.itd.app.data.remote.PlatformApi
import com.itd.app.util.LogTag

/** Compares dotted versions numerically ("1.10.0" > "1.9.3"). */
fun compareVersions(a: String, b: String): Int {
    val left = a.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val right = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(left.size, right.size)) {
        val diff = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
        if (diff != 0) return diff
    }
    return 0
}

enum class UpdateLevel { None, Optional, Required }

/** Asks the server which app versions are supported and shows the update prompt above [content]. */
@Composable
fun UpdateGate(api: PlatformApi, content: @Composable () -> Unit) {
    var info by remember { mutableStateOf<PlatformVersion?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        info = try {
            api.version().android
        } catch (e: Exception) {
            LogTag.APP.tree().w("version check failed: %s", e.message)
            null
        }
    }
    val current = BuildConfig.VERSION_NAME.removeSuffix("-debug")
    val level = info?.let {
        when {
            compareVersions(current, it.minVersion) < 0 -> UpdateLevel.Required
            compareVersions(current, it.latestVersion) < 0 -> UpdateLevel.Optional
            else -> UpdateLevel.None
        }
    } ?: UpdateLevel.None
    val context = LocalContext.current
    val openStore = { info?.updateUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
    if (level == UpdateLevel.Required) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text("Требуется обновление", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Текущая версия приложения больше не поддерживается. Пожалуйста, обновите приложение.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { openStore() }, Modifier.fillMaxWidth()) { Text("Обновить") }
        }
        return
    }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    content()
    if (level == UpdateLevel.Optional && !dismissed) {
        Row(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Доступно новое обновление", Modifier.weight(1f), fontSize = 14.sp)
            TextButton(onClick = { openStore() }) { Text("Обновить") }
            TextButton(onClick = { dismissed = true }) { Text("Закрыть") }
        }
    }
    }
}

private fun Modifier.statusBarsPadding() = this.then(Modifier.statusBarsPadding())
