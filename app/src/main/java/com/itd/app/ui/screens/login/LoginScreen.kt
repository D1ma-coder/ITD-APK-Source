package com.itd.app.ui.screens.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.R
import com.itd.app.ui.components.CaptchaDialog
import com.itd.app.ui.components.ItdButton
import com.itd.app.ui.components.ItdTextField
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignUp: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val autofillManager = LocalAutofillManager.current

    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) {
            autofillManager?.commit()
            onLoginSuccess()
        }
    }
    LaunchedEffect(state.loginError, state.emailError, state.passwordError) {
        if (state.loginError == null && state.emailError == null && state.passwordError == null) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(100)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val buttonText = if (state.retrySeconds > 0) {
        val minutes = state.retrySeconds / 60
        val seconds = state.retrySeconds % 60
        if (minutes > 0) "Повторить через %d:%02d".format(minutes, seconds) else "Повторить через %d сек".format(seconds)
    } else {
        "Войти"
    }

    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(60.dp))
            Icon(painterResource(R.drawable.ic_logo), "ITD", Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(32.dp))
            Text("Вход в аккаунт", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Введите данные для входа", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            ItdTextField(
                state.email, viewModel::onEmailChange, "Электронная почта",
                error = state.emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                contentType = ContentType.EmailAddress + ContentType.Username,
            )
            Spacer(Modifier.height(16.dp))
            ItdTextField(
                state.password, viewModel::onPasswordChange, "Пароль",
                password = true,
                error = state.passwordError,
                imeAction = ImeAction.Done,
                onDone = {
                    focusManager.clearFocus()
                    viewModel.login()
                },
                contentType = ContentType.Password,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Забыли пароль?",
                Modifier.align(Alignment.End)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onForgotPassword()
                    }
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(
                state.loginError != null,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            ) {
                Text(
                    state.loginError ?: "",
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
            ItdButton(
                buttonText,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    focusManager.clearFocus()
                    viewModel.login()
                },
                enabled = state.email.isNotBlank() && state.password.isNotBlank() && state.retrySeconds == 0,
                loading = state.isLoading,
            )
            Spacer(Modifier.height(24.dp))
            val signUp = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("Нет аккаунта? ") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                    append("Зарегистрироваться")
                }
            }
            Text(
                signUp,
                Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSignUp()
                }.padding(vertical = 8.dp),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(32.dp))
        }
        val captchaPage = state.captchaPage
        if (state.showCaptchaDialog && captchaPage != null) {
            CaptchaDialog(captchaPage, viewModel::onCaptchaAnswer, viewModel::onCaptchaDialogDismissed, "login")
        }
    }
}
