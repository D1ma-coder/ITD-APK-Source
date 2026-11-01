package com.itd.app.ui.screens.login

import com.itd.app.data.model.CaptchaPage

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val loginError: String? = null,
    val showCaptchaDialog: Boolean = false,
    val captchaPage: CaptchaPage? = null,
    val loginSuccess: Boolean = false,
    val retrySeconds: Int = 0,
)
