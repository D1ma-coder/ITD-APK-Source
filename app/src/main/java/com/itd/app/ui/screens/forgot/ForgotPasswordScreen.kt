package com.itd.app.ui.screens.forgot

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itd.app.ui.components.AuthError
import com.itd.app.ui.components.AuthPage
import com.itd.app.ui.components.AuthTitle
import com.itd.app.ui.components.CaptchaDialog
import com.itd.app.ui.components.ItdButton
import com.itd.app.ui.components.ItdTextField
import com.itd.app.ui.components.OtpField
import com.itd.app.ui.components.ResendCode
import kotlinx.coroutines.delay

@Composable
fun ForgotPasswordScreen(viewModel: ForgotPasswordViewModel, onSuccess: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.generalError, state.emailError, state.otpError, state.newPasswordError, state.confirmPasswordError) {
        if (listOf(state.generalError, state.emailError, state.otpError, state.newPasswordError, state.confirmPasswordError)
                .all { it == null }
        ) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(100)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(state.resetSuccess) { if (state.resetSuccess) onSuccess() }
    LaunchedEffect(state.maxAttemptsExceeded) { if (state.maxAttemptsExceeded) onBack() }

    AuthPage(onBack) {
        AnimatedContent(
            state.step,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "stepTransition",
        ) { step ->
            when (step) {
                ForgotPasswordStep.Email -> EmailStep(state, viewModel, focusManager, haptic)
                ForgotPasswordStep.Otp -> OtpStep(state, viewModel, focusManager, haptic)
                ForgotPasswordStep.NewPassword -> NewPasswordStep(state, viewModel, focusManager, haptic)
            }
        }
    }
    val captchaPage = state.captchaPage
    if (state.showCaptchaDialog && captchaPage != null) {
        CaptchaDialog(captchaPage, viewModel::onCaptchaAnswer, viewModel::onCaptchaDialogDismissed, "password_reset")
    }
}

@Composable
private fun EmailStep(state: ForgotPasswordUiState, viewModel: ForgotPasswordViewModel, focus: FocusManager, haptic: HapticFeedback) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AuthTitle("Восстановление пароля", "Введите email для восстановления")
        ItdTextField(
            state.email, viewModel::onEmailChange, "Электронная почта",
            error = state.emailError,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onDone = {
                focus.clearFocus()
                viewModel.submitEmail()
            },
            contentType = ContentType.EmailAddress,
        )
        Spacer(Modifier.height(24.dp))
        AuthError(state.generalError)
        val text = if (state.retrySeconds > 0) {
            val minutes = state.retrySeconds / 60
            val seconds = state.retrySeconds % 60
            if (minutes > 0) "Повторить через %d:%02d".format(minutes, seconds) else "Повторить через %d сек".format(seconds)
        } else {
            "Отправить код"
        }
        ItdButton(
            text,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                focus.clearFocus()
                viewModel.submitEmail()
            },
            enabled = state.email.isNotBlank() && state.retrySeconds == 0,
            loading = state.isLoading,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun OtpStep(state: ForgotPasswordUiState, viewModel: ForgotPasswordViewModel, focus: FocusManager, haptic: HapticFeedback) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AuthTitle("Введите код", "Код отправлен на " + state.email)
        OtpField(
            state.otp, viewModel::onOtpChange, Modifier.fillMaxWidth(),
            isError = state.otpError != null,
            onComplete = {
                focus.clearFocus()
                viewModel.submitOtp()
            },
        )
        AuthError(state.otpError, Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        ItdButton(
            "Далее",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                focus.clearFocus()
                viewModel.submitOtp()
            },
            enabled = state.otp.length == 6,
        )
        Spacer(Modifier.height(24.dp))
        ResendCode(state.isResending, state.resendCountdown, viewModel::resendCode)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun NewPasswordStep(state: ForgotPasswordUiState, viewModel: ForgotPasswordViewModel, focus: FocusManager, haptic: HapticFeedback) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AuthTitle("Новый пароль", "Введите новый пароль")
        ItdTextField(
            state.newPassword, viewModel::onNewPasswordChange, "Новый пароль",
            password = true,
            error = state.newPasswordError,
            imeAction = ImeAction.Next,
            contentType = ContentType.NewPassword,
        )
        Spacer(Modifier.height(16.dp))
        ItdTextField(
            state.confirmPassword, viewModel::onConfirmPasswordChange, "Подтвердите пароль",
            password = true,
            error = state.confirmPasswordError,
            imeAction = ImeAction.Done,
            onDone = {
                focus.clearFocus()
                viewModel.resetPassword()
            },
            contentType = ContentType.NewPassword,
        )
        Spacer(Modifier.height(24.dp))
        AuthError(state.generalError)
        ItdButton(
            "Подтвердить",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                focus.clearFocus()
                viewModel.resetPassword()
            },
            enabled = state.newPassword.isNotBlank() && state.confirmPassword.isNotBlank(),
            loading = state.isLoading,
        )
        Spacer(Modifier.height(32.dp))
    }
}
