package com.itd.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun AuthFlowScreen(viewModel: AuthFlowViewModel, onBack: () -> Unit, onComplete: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.step) { if (state.step == AuthStep.Complete) onComplete() }
    val register = viewModel.flow == AuthFlow.Register
    val title = when (state.step) {
        AuthStep.Email -> if (register) "Создание аккаунта" else "Восстановление пароля"
        AuthStep.Otp -> "Введите код"
        AuthStep.NewPassword -> "Новый пароль"
        AuthStep.Complete -> "Готово"
    }
    val subtitle = when (state.step) {
        AuthStep.Email -> if (register) "Введите данные для регистрации" else "Введите email для восстановления"
        AuthStep.Otp -> "Код отправлен на ${state.email}"
        AuthStep.NewPassword -> "Введите новый пароль"
        AuthStep.Complete -> ""
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Icon(painterResource(R.drawable.ic_back), "Назад") }
        Spacer(Modifier.height(16.dp))
        Icon(painterResource(R.drawable.ic_logo), "ИТД", Modifier.size(64.dp))
        Spacer(Modifier.height(32.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        when (state.step) {
            AuthStep.Email -> {
                ItdTextField(state.email, viewModel::onEmailChange, "Электронная почта", error = state.emailError, keyboardType = KeyboardType.Email)
                if (register) {
                    Spacer(Modifier.height(16.dp))
                    ItdTextField(state.password, viewModel::onPasswordChange, "Пароль", password = true, error = state.passwordError,
                        keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, onDone = { viewModel.submit() })
                }
            }
            AuthStep.Otp -> ItdTextField(state.otp, viewModel::onOtpChange, "Код из письма", keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done, onDone = { viewModel.submit() })
            AuthStep.NewPassword -> {
                ItdTextField(state.password, viewModel::onPasswordChange, "Новый пароль", password = true, error = state.passwordError, keyboardType = KeyboardType.Password)
                Spacer(Modifier.height(16.dp))
                ItdTextField(state.confirmPassword, viewModel::onConfirmPasswordChange, "Подтвердите пароль", password = true,
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, onDone = { viewModel.submit() })
            }
            AuthStep.Complete -> Unit
        }
        Spacer(Modifier.height(24.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(16.dp)) }
        Button(onClick = { viewModel.submit() }, enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
            if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text(when (state.step) {
                AuthStep.Email -> if (register) "Зарегистрироваться" else "Отправить код"
                AuthStep.Otp -> "Далее"
                AuthStep.NewPassword -> "Подтвердить"
                AuthStep.Complete -> "Готово"
            })
        }
        if (register && state.step == AuthStep.Email) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Spacer(Modifier.height(16.dp))
            Row {
                Text("Уже есть аккаунт? ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Войти", Modifier.clickable(onClick = onBack), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            val terms = androidx.compose.ui.text.buildAnnotatedString {
                append("Создавая аккаунт, вы соглашаетесь с ")
                pushStringAnnotation("url", "https://xn--d1ah4a.com/terms")
                withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("условиями использования") }
                pop()
                append(" и ")
                pushStringAnnotation("url", "https://xn--d1ah4a.com/privacy")
                withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("политикой конфиденциальности") }
                pop()
            }
            androidx.compose.foundation.text.ClickableText(
                terms,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            ) { offset -> terms.getStringAnnotations("url", offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) } }
        }
        if (state.step == AuthStep.Otp) {
            TextButton(onClick = viewModel::resendOtp, enabled = state.resendSeconds == 0) {
                Text(if (state.resendSeconds > 0) "Отправить повторно через ${state.resendSeconds} сек" else "Отправить код повторно")
            }
        }
    }
    state.captchaPage?.let { page -> CaptchaDialog(page, { viewModel.submit(it) }, viewModel::dismissCaptcha, if (register) "register" else "password_reset") }
}
