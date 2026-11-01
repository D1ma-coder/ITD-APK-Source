package com.itd.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.data.model.PostPoll
import com.itd.app.util.TimeFormat
import kotlin.math.roundToInt

@Composable
fun PostPollView(poll: PostPoll, onVote: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var selected by rememberSaveable(poll.id) { mutableStateOf(emptyList<String>()) }
    var showResults by rememberSaveable(poll.id) { mutableStateOf(false) }
    var changing by rememberSaveable(poll.id) { mutableStateOf(false) }
    val results = (poll.hasVoted && !changing) || showResults
    Column(modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(poll.question, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        poll.options.sortedBy { it.position }.forEach { option ->
            val fraction = if (poll.totalVotes > 0) (option.votesCount.toFloat() / poll.totalVotes).coerceIn(0f, 1f) else 0f
            val chosen = option.id in poll.votedOptionIds
            val picked = option.id in selected
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp).clip(RoundedCornerShape(10.dp))
                    .border(1.dp, if (picked) colors.primary else colors.outlineVariant, RoundedCornerShape(10.dp))
                    .clickable(enabled = !results) {
                        selected = if (poll.multipleChoice) {
                            if (picked) selected - option.id else selected + option.id
                        } else {
                            listOf(option.id)
                        }
                    },
            ) {
                if (results) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background((if (chosen) colors.primary else colors.onSurfaceVariant).copy(alpha = 0.18f)))
                }
                Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (results && chosen) "✓ " else "") + option.text, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
                    if (results) Text("${(fraction * 100).roundToInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${poll.totalVotes} ${TimeFormat.plural(poll.totalVotes, "голос", "голоса", "голосов")}",
                Modifier.weight(1f),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
            when {
                !results -> {
                    if (!poll.hasVoted) {
                        Text("Показать результаты", Modifier.clickable { showResults = true }.padding(6.dp), fontSize = 13.sp, color = colors.primary)
                    }
                    Text(
                        "Проголосовать",
                        Modifier.clickable(enabled = selected.isNotEmpty()) {
                            onVote(selected)
                            changing = false
                            selected = emptyList()
                        }.padding(6.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected.isNotEmpty()) colors.primary else colors.onSurfaceVariant,
                    )
                }
                poll.hasVoted -> Text("Изменить голос", Modifier.clickable { changing = true }.padding(6.dp), fontSize = 13.sp, color = colors.primary)
                else -> Text("Проголосовать", Modifier.clickable { showResults = false }.padding(6.dp), fontSize = 13.sp, color = colors.primary)
            }
        }
    }
}
