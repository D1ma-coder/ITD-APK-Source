package com.itd.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.R

/** Frame of the email confirmation and password recovery screens: back button, logo, scrolling column. */
@Composable
fun AuthPage(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) { focusManager.clearFocus() }
            .statusBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            },
            modifier = Modifier.align(Alignment.Start).padding(top = 4.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(24.dp))
        Icon(painterResource(R.drawable.ic_logo), "ITD", Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(32.dp))
        content()
    }
}

@Composable
fun AuthTitle(title: String, subtitle: String) {
    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    Spacer(Modifier.height(32.dp))
}

@Composable
fun AuthError(error: String?, modifier: Modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
    AnimatedVisibility(error != null, enter = fadeIn(tween(200)) + expandVertically(tween(200))) {
        Text(error ?: "", modifier, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** "Отправить код повторно" with its countdown and progress states. */
@Composable
fun ResendCode(isResending: Boolean, countdown: Int, onResend: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    when {
        isResending -> CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        countdown > 0 -> Text(
            "Отправить повторно через $countdown сек",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        else -> Text(
            "Отправить код повторно",
            Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onResend()
            }.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
