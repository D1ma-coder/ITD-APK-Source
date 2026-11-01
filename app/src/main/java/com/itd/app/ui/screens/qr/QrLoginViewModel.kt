package com.itd.app.ui.screens.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.QrCodeRequest
import com.itd.app.data.model.QrLoginTarget
import com.itd.app.data.remote.ApiException
import com.itd.app.data.remote.AuthApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface QrLoginState {
    data object Scanning : QrLoginState
    data object Loading : QrLoginState
    data class Confirmation(val target: QrLoginTarget) : QrLoginState
    data class Complete(val approved: Boolean) : QrLoginState
    data class Error(val title: String, val message: String, val retryable: Boolean) : QrLoginState
}

class QrLoginViewModel(private val api: AuthApi) : ViewModel() {
    private val _state = MutableStateFlow<QrLoginState>(QrLoginState.Scanning)
    val state = _state.asStateFlow()
    private val _invalidCode = MutableStateFlow(false)
    val invalidCode = _invalidCode.asStateFlow()
    private val _invalidLink = MutableStateFlow(false)

    /** Set when the app was opened with a /qr link that is not a login code. */
    val invalidLink = _invalidLink.asStateFlow()

    fun onLink(value: String) {
        if (QrLoginParser.parse(value) == null) _invalidLink.value = true else onCode(value)
    }

    fun dismissInvalidLink() {
        _invalidLink.value = false
    }
    private var payload: QrLoginPayload? = null

    fun onCode(value: String) {
        if (_state.value != QrLoginState.Scanning) return
        val parsed = QrLoginParser.parse(value)
        _invalidCode.value = parsed == null
        if (parsed == null) return
        payload = parsed
        _state.value = QrLoginState.Loading
        viewModelScope.launch {
            try { _state.value = QrLoginState.Confirmation(api.scanQr(QrCodeRequest(parsed.qrId, parsed.secret))) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { showError(error) }
        }
    }

    fun approve() = decide(true)
    fun reject() = decide(false)
    fun scanAgain() { payload = null; _invalidCode.value = false; _state.value = QrLoginState.Scanning }

    private fun decide(approve: Boolean) {
        if (_state.value !is QrLoginState.Confirmation) return
        val value = payload ?: return
        _state.value = QrLoginState.Loading
        viewModelScope.launch {
            try {
                val request = QrCodeRequest(value.qrId, value.secret)
                if (approve) api.approveQr(request) else api.rejectQr(request)
                _state.value = QrLoginState.Complete(approve)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { showError(error) }
        }
    }

    private fun showError(error: Exception) {
        _state.value = if (error is ApiException) {
            when {
                error.status == 401 || error.code == "SESSION_REVOKED" -> QrLoginState.Error("Сессия недействительна", "Сессия недействительна. Войдите в аккаунт заново.", false)
                error.status == 429 -> QrLoginState.Error("Слишком много попыток", error.retryAfter?.let { "Слишком много попыток. Попробуйте через $it с." } ?: "Слишком много попыток. Попробуйте позже.", true)
                error.code == "QR_ALREADY_USED" -> QrLoginState.Error("Код уже использован", "Этот код уже использован. Обновите его на компьютере.", true)
                error.code == "QR_SECRET_MISMATCH" -> QrLoginState.Error("Код не подходит", "Код не подходит. Убедитесь, что сканируете код со страницы входа.", true)
                error.code == "QR_EXPIRED" -> QrLoginState.Error("Код устарел", "Код устарел. Обновите его на компьютере и отсканируйте заново.", true)
                error.code == "QR_APPROVER_NOT_ALLOWED" -> QrLoginState.Error("Нужно приложение", "Подтвердить вход можно только из мобильного приложения.", false)
                else -> QrLoginState.Error("Не удалось войти", error.message.take(180).ifBlank { "Попробуйте ещё раз." }, false)
            }
        } else QrLoginState.Error("Не удалось войти", "Проверьте связь и попробуйте ещё раз.", true)
    }
}
