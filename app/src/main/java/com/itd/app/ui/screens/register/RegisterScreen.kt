package com.itd.app.ui.screens.register

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.R
import com.itd.app.ui.components.CaptchaDialog
import com.itd.app.ui.components.ItdButton
import com.itd.app.ui.components.ItdTextField
import kotlinx.coroutines.delay

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onSignUpResult: (email: String, password: String, flowToken: String) -> Unit,
    onLogin: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    LaunchedEffect(state.registerError, state.emailError, state.passwordError) {
        if (state.registerError == null && state.emailError == null && state.passwordError == null) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(100)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(state.signUpResult) {
        val result = state.signUpResult ?: return@LaunchedEffect
        onSignUpResult(result.email, result.password, result.flowToken)
        viewModel.onSignUpResultHandled()
    }

    val buttonText = if (state.retrySeconds > 0) {
        val minutes = state.retrySeconds / 60
        val seconds = state.retrySeconds % 60
        if (minutes > 0) "Повторить через %d:%02d".format(minutes, seconds) else "Повторить через %d сек".format(seconds)
    } else {
        "Зарегистрироваться"
    }

    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) { focusManager.clearFocus() },
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
            Text("Создание аккаунта", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Введите данные для регистрации", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            ItdTextField(
                state.email, viewModel::onEmailChange, "Электронная почта",
                error = state.emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                contentType = ContentType.NewUsername + ContentType.EmailAddress,
            )
            Spacer(Modifier.height(16.dp))
            ItdTextField(
                state.password, viewModel::onPasswordChange, "Пароль",
                password = true,
                error = state.passwordError,
                imeAction = ImeAction.Done,
                onDone = {
                    focusManager.clearFocus()
                    viewModel.register()
                },
                contentType = ContentType.NewPassword,
            )
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(state.registerError != null, enter = fadeIn(tween(200)) + expandVertically(tween(200))) {
                Text(
                    state.registerError ?: "",
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
                    viewModel.register()
                },
                enabled = state.email.isNotBlank() && state.password.isNotBlank() && state.retrySeconds == 0,
                loading = state.isLoading,
            )
            Spacer(Modifier.height(24.dp))
            val login = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("Уже есть аккаунт? ") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) { append("Войти") }
            }
            Text(
                login,
                Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLogin()
                }.padding(vertical = 8.dp),
                fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            val terms = buildAnnotatedString {
                withStyle(SpanStyle(color = muted)) { append("Создавая аккаунт, вы соглашаетесь с ") }
                pushStringAnnotation("URL", "https://xn--d1ah4a.com/terms")
                withStyle(SpanStyle(color = muted)) { append("условиями использования") }
                pop()
                withStyle(SpanStyle(color = muted)) { append(" и ") }
                pushStringAnnotation("URL", "https://xn--d1ah4a.com/privacy")
                withStyle(SpanStyle(color = muted)) { append("политикой конфиденциальности") }
                pop()
            }
            @Suppress("DEPRECATION")
            ClickableText(
                terms,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, textAlign = TextAlign.Center),
            ) { offset ->
                val url = terms.getStringAnnotations("URL", offset, offset).firstOrNull() ?: return@ClickableText
                val browser = context.packageManager
                    .resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://")), PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.item))
                if (browser != null) intent.setPackage(browser)
                context.startActivity(intent)
            }
        }
        val captchaPage = state.captchaPage
        if (state.showCaptchaDialog && captchaPage != null) {
            CaptchaDialog(captchaPage, viewModel::onCaptchaAnswer, viewModel::onCaptchaDialogDismissed, "register")
        }
    }
}
