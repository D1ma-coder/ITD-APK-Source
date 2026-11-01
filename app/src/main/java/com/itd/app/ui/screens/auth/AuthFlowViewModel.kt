package com.itd.app.ui.screens.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.*
import com.itd.app.data.remote.ApiException
import com.itd.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthFlow { Register, ForgotPassword }
enum class AuthStep { Email, Otp, NewPassword, Complete }

data class AuthFormState(
    val step: AuthStep = AuthStep.Email,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val otp: String = "",
    val flowToken: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
    val resendSeconds: Int = 0,
    val captchaPage: CaptchaPage? = null,
)

class AuthFlowViewModel(val flow: AuthFlow, private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthFormState())
    val state = _state.asStateFlow()
    private var resendJob: Job? = null

    fun onEmailChange(email: String) = _state.update { it.copy(email = email, emailError = null, error = null) }
    fun onPasswordChange(password: String) = _state.update { it.copy(password = password, passwordError = null, error = null) }
    fun onConfirmPasswordChange(password: String) = _state.update { it.copy(confirmPassword = password, error = null) }
    fun onOtpChange(otp: String) = _state.update { it.copy(otp = otp.filter(Char::isDigit).take(6), error = null) }
    fun dismissCaptcha() = _state.update { it.copy(captchaPage = null) }

    fun submit(captcha: CaptchaAnswer? = null) {
        val value = _state.value
        if (value.isLoading) return
        if (value.step == AuthStep.Email) {
            val email = value.email.trim()
            val emailError = when {
                email.isBlank() -> "Введите электронную почту"
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Неверный формат электронной почты"
                else -> null
            }
            val passwordError = if (flow == AuthFlow.Register) passwordError(value.password) else null
            _state.update { it.copy(emailError = emailError, passwordError = passwordError, captchaPage = null) }
            if (emailError != null || passwordError != null) return
            if (captcha == null) {
                viewModelScope.launch {
                    val page = try { repository.captchaPage() } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        CaptchaPage.FALLBACK
                    }
                    _state.update { it.copy(captchaPage = page) }
                }
                return
            }
        }
        if (value.step == AuthStep.Otp && value.otp.length != 6) return
        if (value.step == AuthStep.NewPassword) {
            val error = passwordError(value.password) ?: if (value.password != value.confirmPassword) "Пароли не совпадают" else null
            if (error != null) { _state.update { it.copy(passwordError = error) }; return }
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                when (value.step) {
                    AuthStep.Email -> {
                        val result = if (flow == AuthFlow.Register) {
                            repository.signUp(value.email.trim(), value.password, captcha).let { it.email to it.flowToken }
                        } else {
                            repository.forgotPassword(value.email.trim(), captcha).let { it.email to it.flowToken }
                        }
                        _state.update { it.copy(step = AuthStep.Otp, email = result.first, flowToken = result.second, isLoading = false) }
                        startResendTimer()
                    }
                    AuthStep.Otp -> if (flow == AuthFlow.Register) {
                        repository.verifyOtp(VerifyOtpRequest(value.email, value.password, value.otp, value.flowToken))
                        _state.update { it.copy(step = AuthStep.Complete, isLoading = false) }
                    } else {
                        _state.update { it.copy(step = AuthStep.NewPassword, password = "", isLoading = false) }
                    }
                    AuthStep.NewPassword -> {
                        repository.resetPassword(ResetPasswordRequest(value.email, value.flowToken, value.otp, value.password))
                        _state.update { it.copy(step = AuthStep.Complete, isLoading = false) }
                    }
                    AuthStep.Complete -> Unit
                }
            } catch (error: CancellationException) { throw error }
            catch (error: ApiException) {
                if (error.code.contains("CAPTCHA", true)) {
                    val page = try { repository.captchaPage() } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        CaptchaPage.FALLBACK
                    }
                    _state.update { it.copy(isLoading = false, captchaPage = page) }
                } else when (error.code) {
                    "CONFLICT" -> _state.update { it.copy(isLoading = false, emailError = "Этот email уже зарегистрирован") }
                    "INVALID_PASSWORD" -> _state.update { it.copy(isLoading = false, passwordError = "Пароль: 8-100 символов, минимум 1 буква и 1 цифра") }
                    else -> _state.update { it.copy(isLoading = false, error = error.message) }
                }
            } catch (error: java.io.IOException) {
                _state.update { it.copy(isLoading = false, error = "Ошибка сети. Проверьте подключение к интернету") }
            } catch (error: Exception) { _state.update { it.copy(isLoading = false, error = "Произошла ошибка. Попробуйте снова") } }
        }
    }

    fun resendOtp() {
        val state = _state.value
        if (state.resendSeconds > 0 || state.isLoading) return
        viewModelScope.launch {
            try {
                repository.resendOtp(ResendOtpRequest(state.email, state.flowToken))
                startResendTimer()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(error = error.message) } }
        }
    }

    private fun startResendTimer() {
        resendJob?.cancel()
        resendJob = viewModelScope.launch {
            for (seconds in 60 downTo 1) { _state.update { it.copy(resendSeconds = seconds) }; delay(1_000) }
            _state.update { it.copy(resendSeconds = 0) }
        }
    }
}

internal fun passwordError(password: String): String? = when {
    password.isBlank() -> "Введите пароль"
    password.length !in 8..100 -> "Пароль должен быть от 8 до 100 символов"
    password.none(Char::isLetter) -> "Пароль должен содержать хотя бы одну букву"
    password.none(Char::isDigit) -> "Пароль должен содержать хотя бы одну цифру"
    else -> null
}
