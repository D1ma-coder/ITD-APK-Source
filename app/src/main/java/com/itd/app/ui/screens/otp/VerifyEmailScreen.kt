package com.itd.app.ui.screens.otp

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.itd.app.ui.components.AuthError
import com.itd.app.ui.components.AuthPage
import com.itd.app.ui.components.AuthTitle
import com.itd.app.ui.components.ItdButton
import com.itd.app.ui.components.OtpField
import com.itd.app.ui.components.ResendCode
import kotlinx.coroutines.delay

@Composable
fun VerifyEmailScreen(viewModel: OtpViewModel, onVerified: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.otpError) {
        if (state.otpError == null) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(100)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(state.verificationSuccess) { if (state.verificationSuccess) onVerified() }
    LaunchedEffect(state.maxAttemptsExceeded) { if (state.maxAttemptsExceeded) onBack() }

    AuthPage(onBack) {
        AuthTitle("Подтверждение почты", "Код отправлен на " + state.email)
        OtpField(
            state.otp, viewModel::onOtpChange, Modifier.fillMaxWidth(),
            isError = state.otpError != null,
            onComplete = {
                focusManager.clearFocus()
                viewModel.verify()
            },
        )
        AuthError(state.otpError, Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        ItdButton(
            "Подтвердить",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                focusManager.clearFocus()
                viewModel.verify()
            },
            enabled = state.otp.length == 6,
            loading = state.isLoading,
        )
        Spacer(Modifier.height(24.dp))
        ResendCode(state.isResending, state.resendCountdown, viewModel::resend)
        Spacer(Modifier.height(32.dp))
    }
}
