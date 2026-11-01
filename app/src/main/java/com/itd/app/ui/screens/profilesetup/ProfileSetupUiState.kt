package com.itd.app.ui.screens.profilesetup

import com.itd.app.data.model.User

enum class ProfileSetupStep { NameUsername, EmojiAvatar }

data class ProfileSetupUiState(
    val step: ProfileSetupStep = ProfileSetupStep.NameUsername,
    val displayName: String = "",
    val username: String = "",
    val displayNameError: String? = null,
    val usernameError: String? = null,
    val isCheckingUsername: Boolean = false,
    val usernameAvailable: Boolean? = null,
    val selectedEmoji: String? = null,
    val isLoading: Boolean = false,
    val generalError: String? = null,
    val profileCreated: Boolean = false,
    val createdUser: User? = null,
)
