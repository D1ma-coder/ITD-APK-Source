package com.itd.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.itd.app.data.remote.DevicesApi
import com.itd.app.data.repository.AuthRepository
import com.itd.app.util.LogTag
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RootViewModel(private val repository: AuthRepository, private val devices: DevicesApi) : ViewModel() {
    val authState = repository.authState
    private val _startupError = MutableStateFlow<String?>(null)
    val startupError = _startupError.asStateFlow()
    init {
        restoreSession()
        viewModelScope.launch {
            authState.filterNotNull().distinctUntilChangedBy { it.authenticated && it.userId != null }.collect { state ->
                if (state.authenticated) registerDevice()
            }
        }
    }

    private suspend fun registerDevice() {
        try {
            devices.register(FirebaseMessaging.getInstance().token.await())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LogTag.PUSH.tree().w(error, "FCM token unavailable")
        }
    }

    fun restoreSession() = viewModelScope.launch {
        _startupError.value = null
        try { repository.restoreSession() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { _startupError.value = error.message ?: "Не удалось подключиться к серверу" }
    }
    fun logout() = viewModelScope.launch {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token != null) devices.unregister(token)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
        try { repository.logout() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
    }
}
