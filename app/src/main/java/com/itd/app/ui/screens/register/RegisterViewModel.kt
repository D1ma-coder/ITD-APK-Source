package com.itd.app.ui.screens.register

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.CaptchaAnswer
import com.itd.app.data.remote.ApiException
import com.itd.app.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class RegisterViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState = _uiState.asStateFlow()
    private var retryJob: Job? = null

    fun onEmailChange(email: String) = _uiState.update { it.copy(email = email, emailError = null, registerError = null) }

    fun onPasswordChange(password: String) =
        _uiState.update { it.copy(password = password, passwordError = null, registerError = null) }

    fun onCaptchaDialogDismissed() = _uiState.update { it.copy(showCaptchaDialog = false) }

    fun onSignUpResultHandled() = _uiState.update { it.copy(signUpResult = null) }

    fun register() {
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
        val password = _uiState.value.password
        if (password.isBlank()) {
            _uiState.update { it.copy(passwordError = "Введите пароль") }
            return
        }
        if (password.length < 8 || password.length > 100) {
            _uiState.update { it.copy(passwordError = "Пароль должен быть от 8 до 100 символов") }
            return
        }
        if (password.none(Char::isLetter)) {
            _uiState.update { it.copy(passwordError = "Пароль должен содержать хотя бы одну букву") }
            return
        }
        if (password.none(Char::isDigit)) {
            _uiState.update { it.copy(passwordError = "Пароль должен содержать хотя бы одну цифру") }
            return
        }
        _uiState.update { it.copy(emailError = null, passwordError = null, registerError = null) }
        viewModelScope.launch {
            val page = repository.captchaPage()
            _uiState.update { it.copy(showCaptchaDialog = true, captchaPage = page) }
        }
    }

    fun onCaptchaAnswer(answer: CaptchaAnswer) {
        val state = _uiState.value
        _uiState.update { it.copy(showCaptchaDialog = false) }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, registerError = null) }
                val response = repository.signUp(state.email.trim(), state.password, answer)
                _uiState.update {
                    it.copy(isLoading = false, signUpResult = SignUpResult(state.email.trim(), state.password, response.flowToken))
                }
            } catch (e: ApiException) {
                val next = when (e.code) {
                    "TOO_MANY_REQUESTS", "TOO_MANY_ATTEMPTS" -> {
                        startRetryTimer(e.retryAfter ?: 60)
                        _uiState.value.copy(isLoading = false)
                    }
                    "CONFLICT" -> _uiState.value.copy(emailError = "Этот email уже зарегистрирован", isLoading = false)
                    "TURNSTILE_VERIFICATION_FAILED" -> _uiState.value.copy(isLoading = false, showCaptchaDialog = true)
                    "INVALID_PASSWORD" ->
                        _uiState.value.copy(passwordError = "Пароль: 8-100 символов, минимум 1 буква и 1 цифра", isLoading = false)
                    else -> _uiState.value.copy(isLoading = false, registerError = e.message)
                }
                _uiState.update { next }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, registerError = "Ошибка сети. Проверьте подключение к интернету") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, registerError = "Произошла ошибка. Попробуйте снова") }
            }
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
    }
}
