package com.itd.app.ui.screens.forgot

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.CaptchaAnswer
import com.itd.app.data.model.CaptchaPage
import com.itd.app.data.model.ResendOtpRequest
import com.itd.app.data.model.ResetPasswordRequest
import com.itd.app.data.remote.ApiException
import com.itd.app.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class ForgotPasswordStep { Email, Otp, NewPassword }

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.Email,
    val email: String = "",
    val emailError: String? = null,
    val showCaptchaDialog: Boolean = false,
    val captchaPage: CaptchaPage? = null,
    val isLoading: Boolean = false,
    val generalError: String? = null,
    val retrySeconds: Int = 0,
    val otp: String = "",
    val otpError: String? = null,
    val attemptsLeft: Int? = null,
    val resendCountdown: Int = 0,
    val isResending: Boolean = false,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val newPasswordError: String? = null,
    val confirmPasswordError: String? = null,
    val resetSuccess: Boolean = false,
    val maxAttemptsExceeded: Boolean = false,
)

class ForgotPasswordViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState = _uiState.asStateFlow()
    private var retryJob: Job? = null
    private var countdownJob: Job? = null
    private var flowToken = ""

    fun onEmailChange(email: String) = _uiState.update { it.copy(email = email, emailError = null, generalError = null) }

    fun onOtpChange(otp: String) =
        _uiState.update { it.copy(otp = otp.filter(Char::isDigit).take(6), otpError = null, attemptsLeft = null) }

    fun onNewPasswordChange(password: String) = _uiState.update { it.copy(newPassword = password, generalError = null) }

    fun onConfirmPasswordChange(password: String) = _uiState.update { it.copy(confirmPassword = password, generalError = null) }

    fun onCaptchaDialogDismissed() = _uiState.update { it.copy(showCaptchaDialog = false) }

    fun submitEmail() {
        if (_uiState.value.retrySeconds > 0) return
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(emailError = "Введите электронную почту") }
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(emailError = "Неверный формат электронной почты") }
            return
        }
        _uiState.update { it.copy(emailError = null, generalError = null) }
        viewModelScope.launch {
            val page = repository.captchaPage()
            _uiState.update { it.copy(showCaptchaDialog = true, captchaPage = page) }
        }
    }

    fun onCaptchaAnswer(answer: CaptchaAnswer) {
        _uiState.update { it.copy(showCaptchaDialog = false) }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, generalError = null) }
                val response = repository.forgotPassword(_uiState.value.email.trim(), answer)
                flowToken = response.flowToken
                _uiState.update { it.copy(step = ForgotPasswordStep.Otp, isLoading = false) }
                startCountdown()
            } catch (e: ApiException) {
                when (e.code) {
                    "TURNSTILE_VERIFICATION_FAILED" -> _uiState.update { it.copy(showCaptchaDialog = true, isLoading = false) }
                    "TOO_MANY_ATTEMPTS", "TOO_MANY_REQUESTS" -> {
                        startRetryTimer(e.retryAfter ?: 60)
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    else -> _uiState.update { it.copy(isLoading = false, generalError = e.message) }
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, generalError = "Ошибка сети. Проверьте подключение к интернету") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, generalError = "Произошла ошибка. Попробуйте снова") }
            }
        }
    }

    fun submitOtp() = _uiState.update { it.copy(step = ForgotPasswordStep.NewPassword) }

    fun resetPassword() {
        val state = _uiState.value
        val password = state.newPassword
        val error = when {
            password.isBlank() -> "Введите пароль"
            password.length < 8 || password.length > 100 -> "Пароль должен быть от 8 до 100 символов"
            password.none(Char::isLetter) -> "Пароль должен содержать хотя бы одну букву"
            password.none(Char::isDigit) -> "Пароль должен содержать хотя бы одну цифру"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(newPasswordError = error) }
            return
        }
        if (state.confirmPassword != password) {
            _uiState.update { it.copy(confirmPasswordError = "Пароли не совпадают") }
            return
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, generalError = null) }
                val current = _uiState.value
                repository.resetPassword(ResetPasswordRequest(current.email.trim(), flowToken, current.otp, current.newPassword))
                _uiState.update { it.copy(isLoading = false, resetSuccess = true) }
            } catch (e: ApiException) {
                _uiState.update {
                    when (e.code) {
                        "OTP_MAX_ATTEMPTS", "TOO_MANY_ATTEMPTS" -> it.copy(isLoading = false, maxAttemptsExceeded = true)
                        "OTP_INVALID" -> it.copy(
                            step = ForgotPasswordStep.Otp, isLoading = false, otp = "",
                            otpError = "Неверный код", attemptsLeft = e.attemptsLeft,
                        )
                        "INVALID_PASSWORD" ->
                            it.copy(isLoading = false, newPasswordError = "Пароль: 8-100 символов, минимум 1 буква и 1 цифра")
                        else -> it.copy(isLoading = false, generalError = e.message)
                    }
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, generalError = "Ошибка сети. Проверьте подключение к интернету") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, generalError = "Произошла ошибка. Попробуйте снова") }
            }
        }
    }

    fun resendCode() {
        val state = _uiState.value
        if (state.resendCountdown > 0 || state.isResending) return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isResending = true) }
                repository.resendOtp(ResendOtpRequest(_uiState.value.email.trim(), flowToken))
                _uiState.update { it.copy(otp = "", otpError = null, attemptsLeft = null, isResending = false) }
                startCountdown()
            } catch (e: ApiException) {
                _uiState.update { it.copy(otpError = e.message, isResending = false) }
            } catch (e: IOException) {
                _uiState.update { it.copy(otpError = "Ошибка сети", isResending = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(otpError = "Произошла ошибка", isResending = false) }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var left = 60
            while (left > 0) {
                _uiState.update { it.copy(resendCountdown = left) }
                delay(1000)
                left--
            }
            _uiState.update { it.copy(resendCountdown = 0) }
        }
    }

    private fun startRetryTimer(seconds: Int) {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                _uiState.update { it.copy(retrySeconds = left) }
                delay(1000)
                left--
            }
            _uiState.update { it.copy(retrySeconds = 0) }
        }
    }

    override fun onCleared() {
        retryJob?.cancel()
        countdownJob?.cancel()
    }
}
