package com.itd.app.data.repository

import com.itd.app.data.local.TokenStore
import com.itd.app.data.model.*
import com.itd.app.data.remote.ApiClient
import com.itd.app.data.remote.AuthApi
import com.itd.app.data.remote.SessionCreation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val api: AuthApi,
    private val client: ApiClient,
    private val tokenStore: TokenStore,
) {
    private val _authState = MutableStateFlow<AuthState?>(null)
    val authState = _authState.asStateFlow()

    init { client.onSessionExpired = { _authState.value = AuthState(authenticated = false) } }

    suspend fun restoreSession(): AuthState {
        if (client.refreshSession()) return loadProfile()
        return AuthState(authenticated = false).also { _authState.value = it }
    }

    suspend fun signIn(email: String, password: String, captcha: CaptchaAnswer? = null) {
        val response = api.signIn(LoginRequest(email, password, captcha?.token?.takeIf { captcha.field == "token" }, captcha?.token?.takeIf { captcha.field != "token" }))
        tokenStore.accessToken = response.accessToken
        SessionCreation.lastCreatedAt = System.currentTimeMillis()
        loadProfile()
    }

    suspend fun verifyOtp(request: VerifyOtpRequest) {
        tokenStore.accessToken = api.verifyOtp(request).accessToken
        SessionCreation.lastCreatedAt = System.currentTimeMillis()
        loadProfile()
    }

    suspend fun loadProfile(): AuthState = client.get<AuthState>("/api/profile").also { _authState.value = it }

    suspend fun captchaPage(): CaptchaPage = api.captchaPage()

    suspend fun signUp(email: String, password: String, captcha: CaptchaAnswer? = null): SignUpResponse =
        api.signUp(SignUpRequest(email, password, captcha?.token?.takeIf { captcha.field == "token" }, captcha?.token?.takeIf { captcha.field != "token" }))

    suspend fun forgotPassword(email: String, captcha: CaptchaAnswer? = null): ForgotPasswordResponse =
        api.forgotPassword(ForgotPasswordRequest(email, captcha?.token?.takeIf { captcha.field == "token" }, captcha?.token?.takeIf { captcha.field != "token" }))

    suspend fun resetPassword(request: ResetPasswordRequest): ResetPasswordResponse = api.resetPassword(request)
    suspend fun resendOtp(request: ResendOtpRequest): ResendOtpResponse = api.resendOtp(request)

    suspend fun logout() {
        try {
            api.logout()
        } finally {
            tokenStore.clear()
            client.cookies.clear()
            _authState.value = AuthState(authenticated = false)
        }
    }
}
