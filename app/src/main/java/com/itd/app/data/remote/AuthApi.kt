package com.itd.app.data.remote

import com.itd.app.data.model.*
import io.ktor.http.encodeURLPathPart

class AuthApi(private val client: ApiClient) {
    suspend fun captchaPage(): CaptchaPage = client.get("/api/v1/auth/captcha/page")
    suspend fun signIn(request: LoginRequest): LoginResponse = client.post("/api/v1/auth/sign-in", request)
    suspend fun signUp(request: SignUpRequest): SignUpResponse = client.post("/api/v1/auth/sign-up", request)
    suspend fun verifyOtp(request: VerifyOtpRequest): LoginResponse = client.post("/api/v1/auth/verify-otp", request)
    suspend fun resendOtp(request: ResendOtpRequest): ResendOtpResponse = client.post("/api/v1/auth/resend-otp", request)
    suspend fun forgotPassword(request: ForgotPasswordRequest): ForgotPasswordResponse = client.post("/api/v1/auth/forgot-password", request)
    suspend fun resetPassword(request: ResetPasswordRequest): ResetPasswordResponse = client.post("/api/v1/auth/reset-password", request)
    suspend fun changePassword(request: ChangePasswordRequest): ChangePasswordResponse = client.post("/api/v1/auth/change-password", request)
    suspend fun sessions(): SessionsResponse = client.get("/api/v1/auth/sessions")
    suspend fun revokeSession(id: String) = client.delete("/api/v1/auth/sessions/${id.encodeURLPathPart()}")
    suspend fun revokeAllSessions(): RevokeAllSessionsResponse =
        client.request(io.ktor.http.HttpMethod.Delete, "/api/v1/auth/sessions", null, emptyMap(), RevokeAllSessionsResponse.serializer())
    suspend fun logout() = client.postEmpty("/api/v1/auth/logout")
    suspend fun scanQr(request: QrCodeRequest): QrLoginTarget = client.post("/api/v1/auth/qr/scan", request)
    suspend fun approveQr(request: QrCodeRequest) { client.send(io.ktor.http.HttpMethod.Post, "/api/v1/auth/qr/approve", client.json.encodeToJsonElement(QrCodeRequest.serializer(), request)) }
    suspend fun rejectQr(request: QrCodeRequest) { client.send(io.ktor.http.HttpMethod.Post, "/api/v1/auth/qr/reject", client.json.encodeToJsonElement(QrCodeRequest.serializer(), request)) }
}
