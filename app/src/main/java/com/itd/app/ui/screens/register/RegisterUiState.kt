package com.itd.app.ui.screens.register

import com.itd.app.data.model.CaptchaPage

data class SignUpResult(val email: String, val password: String, val flowToken: String)

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val registerError: String? = null,
    val showCaptchaDialog: Boolean = false,
    val captchaPage: CaptchaPage? = null,
    val retrySeconds: Int = 0,
    val signUpResult: SignUpResult? = null,
)
