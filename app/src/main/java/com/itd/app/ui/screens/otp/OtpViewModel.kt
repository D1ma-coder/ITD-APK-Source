package com.itd.app.ui.screens.otp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.ResendOtpRequest
import com.itd.app.data.model.VerifyOtpRequest
import com.itd.app.data.remote.ApiException
import com.itd.app.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class OtpUiState(
    val email: String,
    val otp: String = "",
    val otpError: String? = null,
    val attemptsLeft: Int? = null,
    val isLoading: Boolean = false,
    val resendCountdown: Int = 60,
    val isResending: Boolean = false,
    val verificationSuccess: Boolean = false,
    val maxAttemptsExceeded: Boolean = false,
)

class OtpViewModel(
    private val repository: AuthRepository,
    private val email: String,
    private val password: String,
    private val flowToken: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OtpUiState(email))
    val uiState = _uiState.asStateFlow()
    private var countdownJob: Job? = null

    init {
        startCountdown()
    }

    fun onOtpChange(otp: String) =
        _uiState.update { it.copy(otp = otp.filter(Char::isDigit).take(6), otpError = null, attemptsLeft = null) }

    fun verify() {
        val state = _uiState.value
        if (state.otp.length < 6) {
            _uiState.update { it.copy(otpError = "Введите 6-значный код") }
            return
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(otpError = null, isLoading = true) }
                repository.verifyOtp(VerifyOtpRequest(email, password, state.otp, flowToken))
                _uiState.update { it.copy(isLoading = false, verificationSuccess = true) }
            } catch (e: ApiException) {
                _uiState.update {
                    when (e.code) {
                        "OTP_INVALID" -> it.copy(otpError = "Неверный код", attemptsLeft = e.attemptsLeft, isLoading = false)
                        "OTP_MAX_ATTEMPTS", "TOO_MANY_ATTEMPTS" -> it.copy(isLoading = false, maxAttemptsExceeded = true)
                        else -> it.copy(otpError = e.message, isLoading = false)
                    }
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(otpError = "Ошибка сети. Проверьте подключение к интернету", isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(otpError = "Произошла ошибка. Попробуйте снова", isLoading = false) }
            }
        }
    }

    fun resend() {
        val state = _uiState.value
        if (state.resendCountdown > 0 || state.isResending) return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isResending = true) }
                repository.resendOtp(ResendOtpRequest(email, flowToken))
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

    override fun onCleared() {
        countdownJob?.cancel()
    }
}
