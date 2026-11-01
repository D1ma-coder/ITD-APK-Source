package com.itd.app.ui.screens.login

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.CaptchaAnswer
import com.itd.app.data.model.CaptchaPage
import com.itd.app.data.remote.ApiException
import com.itd.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()
    private var retryJob: Job? = null

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, emailError = null, loginError = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null, loginError = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.retrySeconds > 0 || state.isLoading) return
        val email = state.email.trim()
        val emailError = when {
            email.isBlank() -> "Введите электронную почту"
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Неверный формат электронной почты"
            else -> null
        }
        val passwordError = if (state.password.isBlank()) "Введите пароль" else null
        _uiState.update { it.copy(emailError = emailError, passwordError = passwordError, loginError = null) }
        if (emailError != null || passwordError != null) return
        viewModelScope.launch {
            val page = try { authRepository.captchaPage() } catch (error: Exception) {
                if (error is CancellationException) throw error
                CaptchaPage.FALLBACK
            }
            _uiState.update { it.copy(captchaPage = page, showCaptchaDialog = true) }
        }
    }

    fun onCaptchaAnswer(answer: CaptchaAnswer) {
        _uiState.update { it.copy(showCaptchaDialog = false) }
        submit(answer)
    }

    fun onCaptchaDialogDismissed() {
        _uiState.update { it.copy(showCaptchaDialog = false) }
    }

    private fun submit(captcha: CaptchaAnswer?) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, loginError = null) }
        try {
            val state = _uiState.value
            authRepository.signIn(state.email.trim(), state.password, captcha)
            _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            when (error.code) {
                "TOO_MANY_REQUESTS", "TOO_MANY_ATTEMPTS" -> {
                    _uiState.update { it.copy(isLoading = false) }
                    startRetryTimer(error.retryAfter ?: 60)
                }
                "INVALID_CREDENTIALS" -> _uiState.update { it.copy(isLoading = false, passwordError = "Неверный email или пароль") }
                "TURNSTILE_VERIFICATION_FAILED" -> _uiState.update { it.copy(isLoading = false, showCaptchaDialog = true) }
                "ACCOUNT_DEACTIVATED" -> _uiState.update { it.copy(isLoading = false, loginError = "Аккаунт деактивирован") }
                else -> _uiState.update { it.copy(isLoading = false, loginError = error.message) }
            }
        } catch (_: IOException) {
            _uiState.update { it.copy(isLoading = false, loginError = "Ошибка сети. Проверьте подключение к интернету") }
        } catch (error: Exception) {
            _uiState.update { it.copy(isLoading = false, loginError = error.message ?: "Не удалось войти в аккаунт") }
        }
    }

    private fun startRetryTimer(seconds: Int) {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _uiState.update { it.copy(retrySeconds = remaining) }
                delay(1_000)
            }
            _uiState.update { it.copy(retrySeconds = 0) }
        }
    }
}
