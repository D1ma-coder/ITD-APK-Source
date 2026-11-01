package com.itd.app.ui.components.emojipicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private data class EmojiEntry(val emoji: String, val keywords: List<String>)

@Composable
fun EmojiPicker(onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val emojis by produceState<List<EmojiEntry>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val text = context.assets.open("emoji.json").bufferedReader().use { it.readText() }
            Json.parseToJsonElement(text).jsonArray.flatMap { category ->
                category.jsonObject.getValue("emojis").jsonArray.map { entry ->
                    val row = entry.jsonObject
                    EmojiEntry(row.getValue("e").jsonPrimitive.content, row.getValue("k").jsonArray.map { it.jsonPrimitive.content })
                }
            }
        }
    }
    val filtered = remember(emojis, query) { if (query.isBlank()) emojis else emojis.filter { it.emoji.contains(query) || it.keywords.any { key -> key.contains(query, ignoreCase = true) } } }
    Column(modifier) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Поиск эмодзи") })
        LazyVerticalGrid(GridCells.Adaptive(48.dp), Modifier.fillMaxWidth().weight(1f)) {
            items(filtered, key = { it.emoji }) { item ->
                Text(item.emoji, Modifier.clickable { onSelected(item.emoji) }.padding(8.dp), fontSize = 28.sp)
            }
        }
    }
}
