package com.itd.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val ReportReasons = listOf(
    "spam" to "Спам",
    "violence" to "Насилие или опасные действия",
    "hate" to "Ненависть или травля",
    "adult" to "Контент для взрослых",
    "fraud" to "Мошенничество или обман",
    "other" to "Другое",
)

/** Lets the user pick a report reason; [onReport] receives the reason code. */
@Composable
fun ReportDialog(onDismiss: () -> Unit, onReport: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пожаловаться") },
        text = {
            Column {
                ReportReasons.forEach { (code, label) ->
                    Text(label, Modifier.fillMaxWidth().clickable { onReport(code) }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отменить") } },
    )
}
