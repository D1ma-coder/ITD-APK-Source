package com.itd.app.ui.screens.createpost

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.R

/**
 * Poll of the post editor: question, options and a trailing "Добавить вариант..." slot that becomes an
 * option as soon as something is typed into it (up to 10 options).
 */
@Composable
fun PollEditor(
    question: String,
    onQuestionChange: (String) -> Unit,
    options: SnapshotStateList<String>,
    multipleChoice: Boolean,
    onMultipleChoiceChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val trailingEmpty = options.isNotEmpty() && options.last().isEmpty()
    val filled = if (trailingEmpty) options.size - 1 else options.size
    val canAdd = filled < 10
    Column(
        modifier.fillMaxWidth().clip(shape).border(1.dp, colors.outlineVariant.copy(alpha = 0.6f), shape)
            .background(colors.surfaceVariant.copy(alpha = 0.15f)).padding(14.dp)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f)),
    ) {
        BasicTextField(
            question, onQuestionChange,
            Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp),
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Text("Вопрос", color = if (question.isEmpty()) colors.onSurfaceVariant.copy(alpha = 0.4f) else Color.Transparent, fontSize = 15.sp)
                    field()
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        val last = options.lastIndex
        for (index in options.indices) {
            val option = options[index]
            val addSlot = index == last && option.isEmpty()
            val showAdd = addSlot && canAdd
            val count = if (options.isEmpty() || options.last().isNotEmpty()) options.size else options.size - 1
            val removable = count > 2 && !addSlot
            Column {
                PollOptionField(
                    option,
                    { value ->
                        options[index] = value
                        if (index == options.lastIndex && value.isNotEmpty() && options.size < 10) options.add("")
                    },
                    if (showAdd) "Добавить вариант..." else "Вариант ${index + 1}",
                    showAdd, removable,
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (index < options.size) options.removeAt(index)
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMultipleChoiceChange(!multipleChoice)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).then(
                    if (multipleChoice) Modifier.background(colors.primary)
                    else Modifier.border(1.5.dp, colors.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (multipleChoice) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(14.dp), tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text("Несколько вариантов ответа", color = colors.onSurface, fontSize = 14.sp)
        }
    }
}

/** One option: solid frame, or a dashed one while it is the empty "add" slot. */
@Composable
private fun PollOptionField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    addSlot: Boolean,
    removable: Boolean,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dashed by androidx.compose.animation.core.animateFloatAsState(if (addSlot) 1f else 0f, tween(250), label = "")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).drawBehind {
            val stroke = 1.5.dp.toPx()
            val radius = CornerRadius(10.dp.toPx())
            val solid = colors.outline.copy(alpha = 0.5f)
            if (dashed > 0.01f) {
                val dash = 6.dp.toPx()
                val gap = 4.dp.toPx() * dashed
                drawRoundRect(
                    lerp(solid, colors.outline.copy(alpha = 0.35f), dashed), cornerRadius = radius,
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap))),
                )
            } else {
                drawRoundRect(solid, cornerRadius = radius, style = Stroke(stroke))
            }
        }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value, onValueChange, Modifier.weight(1f),
            textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp),
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Text(placeholder, color = if (value.isEmpty()) colors.onSurfaceVariant.copy(alpha = 0.4f) else Color.Transparent, fontSize = 15.sp)
                    field()
                }
            },
        )
        AnimatedVisibility(
            removable,
            enter = expandHorizontally() + fadeIn(tween(150)),
            exit = shrinkHorizontally() + fadeOut(tween(100)),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(20.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_close), "Remove option", Modifier.size(12.dp), tint = colors.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
