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
    data object Checking : QrLoginState
    data class Confirming(val target: QrLoginTarget) : QrLoginState
    data object Approving : QrLoginState
    data object Rejecting : QrLoginState
    data object Approved : QrLoginState
    data object Rejected : QrLoginState
    data class Failed(val title: String, val message: String, val retryable: Boolean) : QrLoginState
}

class QrLoginViewModel(private val api: AuthApi) : ViewModel() {
    private val _state = MutableStateFlow<QrLoginState>(QrLoginState.Scanning)
    val state = _state.asStateFlow()
    private val _invalidCode = MutableStateFlow(false)

    /** The camera sees a QR code that is not an ITD login code. */
    val invalidCode = _invalidCode.asStateFlow()
    private val _invalidLink = MutableStateFlow(false)

    /** Set when the app was opened with a /qr link that is not a login code. */
    val invalidLink = _invalidLink.asStateFlow()

    /** Country of this device (from the SIM network), compared with the country of the sign-in request. */
    var deviceCountry: String? = null
    private var payload: QrLoginPayload? = null

    fun onLink(value: String) {
        if (QrLoginParser.parse(value) == null) _invalidLink.value = true else handleScannedCode(value)
    }

    fun dismissInvalidLink() {
        _invalidLink.value = false
    }

    fun handleScannedCode(value: String) {
        if (_state.value !is QrLoginState.Scanning) return
        val parsed = QrLoginParser.parse(value)
        if (parsed == null) {
            _invalidCode.value = true
            return
        }
        _invalidCode.value = false
        payload = parsed
        _state.value = QrLoginState.Checking
        viewModelScope.launch {
            try {
                _state.value = QrLoginState.Confirming(api.scanQr(QrCodeRequest(parsed.qrId, parsed.secret)))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    /** The code left the frame while scanning: the "not a login code" warning goes away. */
    fun onCodeLost() {
        if (_state.value is QrLoginState.Scanning) _invalidCode.value = false
    }

    fun approve() {
        val value = payload ?: return
        if (_state.value !is QrLoginState.Confirming) return
        _state.value = QrLoginState.Approving
        viewModelScope.launch {
            try {
                api.approveQr(QrCodeRequest(value.qrId, value.secret))
                _state.value = QrLoginState.Approved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    fun reject() {
        val value = payload ?: return
        if (_state.value !is QrLoginState.Confirming) return
        _state.value = QrLoginState.Rejecting
        viewModelScope.launch {
            try {
                api.rejectQr(QrCodeRequest(value.qrId, value.secret))
                _state.value = QrLoginState.Rejected
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    fun retry() {
        payload = null
        _invalidCode.value = false
        _state.value = QrLoginState.Scanning
    }

    private fun fail(error: Exception) {
        _state.value = if (error is ApiException) {
            when {
                error.status == 429 -> QrLoginState.Failed(
                    "Слишком много попыток",
                    error.retryAfter?.let { "Слишком много попыток. Попробуйте через $it с." } ?: "Слишком много попыток. Попробуйте позже.",
                    true,
                )
                error.status == 401 || error.code == "SESSION_REVOKED" -> SESSION_REVOKED
                error.code == "QR_ALREADY_USED" -> QrLoginState.Failed("Код уже использован", "Этот код уже использован. Обновите его на компьютере.", true)
                error.code == "QR_SECRET_MISMATCH" -> QrLoginState.Failed("Код не подходит", "Код не подходит. Убедитесь, что сканируете код со страницы входа.", true)
                error.code == "QR_EXPIRED" -> QrLoginState.Failed("Код устарел", "Код устарел. Обновите его на компьютере и отсканируйте заново.", true)
                error.code == "QR_APPROVER_NOT_ALLOWED" -> QrLoginState.Failed("Нужно приложение", "Подтвердить вход можно только из мобильного приложения.", false)
                else -> QrLoginState.Failed("Не удалось войти", error.message.take(180).ifBlank { "Попробуйте ещё раз." }, false)
            }
        } else {
            QrLoginState.Failed("Не удалось войти", "Проверьте связь и попробуйте ещё раз.", true)
        }
    }

    companion object {
        private val SESSION_REVOKED = QrLoginState.Failed("Сессия недействительна", "Сессия недействительна. Войдите в аккаунт заново.", false)
    }
}
