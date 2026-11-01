package com.itd.app.halloween

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.itd.app.halloween.HalloweenJson.array
import com.itd.app.halloween.HalloweenJson.field
import com.itd.app.halloween.HalloweenJson.int
import com.itd.app.halloween.HalloweenJson.string
import com.itd.app.util.TimeFormat
import kotlinx.serialization.json.JsonElement

/** A user's Halloween pumpkin, earned with thanks for saved posts. */
data class HalloweenPumpkin(val templateId: String?, val thanks: Int, val glow: Int)

fun parsePumpkin(halloween: JsonElement?): HalloweenPumpkin? {
    val effect = halloween.field("effects").array()?.firstOrNull { it.field("assetKey").string()?.startsWith("halloween.pumpkin.") == true }
        ?: return null
    val expires = HalloweenJson.epochMillis(halloween.field("expiresAt").string())
    if (expires != null && expires < HalloweenFeed.now()) return null
    val parameters = effect.field("parameters")
    return HalloweenPumpkin(
        templateId = parameters.field("templateId").string() ?: effect.field("assetKey").string()?.removePrefix("halloween.pumpkin."),
        thanks = parameters.field("thanks").int() ?: 0,
        glow = parameters.field("glow").int() ?: 0,
    )
}

@Composable
fun PumpkinBadge(halloween: JsonElement?, displayName: String, size: Dp = 18.dp) {
    val pumpkin = remember(halloween) { parsePumpkin(halloween) } ?: return
    var open by remember { mutableStateOf(false) }
    AsyncImage(
        HalloweenArt.url("pumpkin-body"),
        null,
        Modifier.padding(start = 4.dp).size(size)
            .semantics { contentDescription = "Хеллоуинская тыква, спасибо: ${pumpkin.thanks}. Жми, чтобы рассмотреть" }
            .clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Тыква: $displayName") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(HalloweenArt.url("pumpkin-body"), null, Modifier.size(120.dp))
                    Text("спасибо от ${pumpkin.thanks} ${TimeFormat.plural(pumpkin.thanks, "человека", "человек", "человек")}")
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Закрыть") } },
        )
    }
}
