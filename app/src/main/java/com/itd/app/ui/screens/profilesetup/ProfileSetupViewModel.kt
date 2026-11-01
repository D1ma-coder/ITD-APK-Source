package com.itd.app.ui.screens.profilesetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.CreateProfileRequest
import com.itd.app.data.remote.UsersApi
import com.itd.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileSetupViewModel(private val usersApi: UsersApi, private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileSetupUiState())
    val state = _state.asStateFlow()
    private var usernameJob: Job? = null
    private val usernameCache = mutableMapOf<String, Boolean>()

    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value, displayNameError = null) }
    fun onEmojiSelected(value: String) = _state.update { it.copy(selectedEmoji = value, generalError = null) }

    fun onUsernameChange(value: String) {
        usernameJob?.cancel()
        _state.update { it.copy(username = value, usernameError = null, usernameAvailable = usernameCache[value], isCheckingUsername = false) }
        if (value.length < 3) return
        usernameCache[value]?.let { return }
        usernameJob = viewModelScope.launch {
            delay(500)
            _state.update { it.copy(isCheckingUsername = true) }
            try {
                val available = usersApi.checkUsername(value).available
                usernameCache[value] = available
                _state.update { it.copy(isCheckingUsername = false, usernameAvailable = available, usernameError = if (available) null else "Этот юзернейм уже занят") }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _state.update { it.copy(isCheckingUsername = false) } }
        }
    }

    fun next() {
        val state = _state.value
        val nameError = if (state.displayName.isBlank()) "Введите имя" else null
        val usernameError = when {
            state.username.length < 3 -> "Минимум 3 символа"
            state.usernameAvailable == false -> "Этот юзернейм уже занят"
            else -> null
        }
        _state.update { it.copy(displayNameError = nameError, usernameError = usernameError) }
        if (nameError == null && usernameError == null && !state.isCheckingUsername) _state.update { it.copy(step = ProfileSetupStep.EmojiAvatar) }
    }

    fun back() = _state.update { it.copy(step = ProfileSetupStep.NameUsername) }

    fun createProfile() {
        val state = _state.value
        val emoji = state.selectedEmoji ?: return
        if (state.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, generalError = null) }
            try {
                usersApi.createProfile(CreateProfileRequest(state.displayName, emoji, state.username))
                val user = authRepository.loadProfile().user
                _state.update { it.copy(isLoading = false, profileCreated = true, createdUser = user) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(isLoading = false, generalError = error.message) } }
        }
    }
}
