package com.itd.app.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itd.app.R
import com.itd.app.ui.components.CaptchaDialog
import com.itd.app.ui.components.ItdTextField

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignUp: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.loginSuccess) { if (state.loginSuccess) onLoginSuccess() }
    val submit = { focusManager.clearFocus(); viewModel.login() }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))
        Icon(painterResource(R.drawable.ic_logo), "ИТД", Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(32.dp))
        Text("Вход в аккаунт", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Введите данные для входа", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        ItdTextField(state.email, viewModel::onEmailChange, "Электронная почта", error = state.emailError, keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(16.dp))
        ItdTextField(state.password, viewModel::onPasswordChange, "Пароль", password = true, error = state.passwordError,
            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, onDone = submit)
        Spacer(Modifier.height(8.dp))
        Text("Забыли пароль?", Modifier.align(Alignment.End).clickable(onClick = onForgotPassword).padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        state.loginError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = submit,
            enabled = state.email.isNotBlank() && state.password.isNotBlank() && !state.isLoading && state.retrySeconds == 0,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .4f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .7f),
            ),
        ) {
            if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(retryText(state.retrySeconds), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.padding(vertical = 8.dp)) {
            Text("Нет аккаунта? ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Зарегистрироваться", Modifier.clickable(onClick = onSignUp), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(32.dp))
    }
    if (state.showCaptchaDialog) state.captchaPage?.let {
        CaptchaDialog(it, viewModel::onCaptchaAnswer, viewModel::onCaptchaDialogDismissed)
    }
}

private fun retryText(seconds: Int): String = when {
    seconds >= 60 -> "Повторить через %d:%02d".format(seconds / 60, seconds % 60)
    seconds > 0 -> "Повторить через $seconds сек"
    else -> "Войти"
}
