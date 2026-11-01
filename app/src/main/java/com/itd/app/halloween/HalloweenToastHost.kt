package com.itd.app.halloween

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun HalloweenToastHost(modifier: Modifier = Modifier) {
    val toasts by HalloweenToasts.toasts.collectAsState()
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        toasts.forEach { toast ->
            Surface(
                Modifier.fillMaxWidth().pointerInput(toast.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        HalloweenToasts.setPaused(toast.id, true)
                        waitForUpOrCancellation()
                        HalloweenToasts.setPaused(toast.id, false)
                    }
                },
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        toast.art?.let { AsyncImage(HalloweenArt.url(it), null, Modifier.size(40.dp)) }
                        if (toast.art != null) Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(toast.title, fontWeight = FontWeight.SemiBold)
                            toast.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        TextButton(onClick = {
                            toast.onDismiss?.invoke()
                            HalloweenToasts.dismiss(toast.id)
                        }) { Text("✕") }
                    }
                    if (toast.actions.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            toast.actions.forEach { action ->
                                TextButton(onClick = {
                                    action.onClick()
                                    HalloweenToasts.dismiss(toast.id)
                                }) { Text(action.label) }
                            }
                        }
                    }
                }
            }
        }
    }
}
