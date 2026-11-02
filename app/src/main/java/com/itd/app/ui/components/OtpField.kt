package com.itd.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Six digit cells over an invisible text field; completes by itself on the sixth digit. */
@Composable
fun OtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onComplete: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(value, onComplete) { if (value.length == 6) onComplete() }
    BasicTextField(
        value,
        { onValueChange(it.filter(Char::isDigit).take(6)) },
        modifier.focusRequester(focusRequester),
        textStyle = TextStyle(color = Color.Transparent, fontSize = 0.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onComplete() }),
        singleLine = true,
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(6) { index ->
                    val char = value.getOrNull(index)
                    val active = index == value.length && !isError
                    val colors = MaterialTheme.colorScheme
                    val target = when {
                        isError -> colors.error
                        active -> colors.primary
                        char != null -> colors.outline
                        else -> colors.outlineVariant
                    }
                    val border by animateColorAsState(target, tween(200), label = "otpBorder$index")
                    Box(
                        Modifier.weight(1f).size(56.dp)
                            .border(if (active || isError) 2.dp else 1.5.dp, border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            char?.toString() ?: "",
                            style = TextStyle(
                                color = if (isError) colors.error else colors.onSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        },
    )
}
