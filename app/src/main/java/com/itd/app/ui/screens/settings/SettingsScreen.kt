package com.itd.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.BuildConfig
import com.itd.app.R
import com.itd.app.data.model.ChangePasswordRequest
import com.itd.app.data.model.NotificationSettings
import com.itd.app.data.model.PrivacySettings
import com.itd.app.data.model.UpdatePrivacyRequest
import com.itd.app.data.remote.ApiException
import com.itd.app.di.AppContainer
import com.itd.app.ui.components.ItdTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onOpen: (String) -> Unit, onBack: () -> Unit, onLogout: () -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    SettingsPage("Настройки", onBack) {
        SettingsSection("Профиль") {
            SettingsRow("Редактировать профиль", icon = R.drawable.ic_profile, divider = false) { onOpen("edit-profile") }
        }
        SettingsSection("Аккаунт") {
            SettingsRow("Подписка", icon = R.drawable.ic_wallet) { onOpen("subscription") }
            SettingsRow("Методы оплаты", icon = R.drawable.ic_card, divider = false) { onOpen("payment-methods") }
        }
        SettingsSection("Основные") {
            SettingsRow("Приватность", icon = R.drawable.ic_privacy) { onOpen("privacy") }
            SettingsRow("Уведомления", icon = R.drawable.ic_notifications) { onOpen("notification-settings") }
            SettingsRow("Безопасность", icon = R.drawable.ic_security, divider = false) { onOpen("security") }
        }
        SettingsSection {
            SettingsRow("Отладка", icon = R.drawable.ic_settings_gear, divider = false) { onOpen("diagnostics") }
        }
        SettingsSection {
            SettingsRow("Выйти из аккаунта", icon = R.drawable.ic_logout, destructive = true, divider = false) { confirmLogout = true }
        }
        Text(
            "Версия ${BuildConfig.VERSION_NAME.removeSuffix("-debug")} (${BuildConfig.VERSION_CODE})",
            Modifier.fillMaxWidth(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта?") },
            confirmButton = { TextButton(onClick = onLogout) { Text("Выйти", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } },
        )
    }
}

@Composable
fun SecurityScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    SettingsPage("Безопасность", onBack) {
        SettingsSection {
            SettingsRow("Активные сессии", icon = R.drawable.ic_sessions) { onOpen("sessions") }
            SettingsRow("Подключить устройство", subtitle = "Войти по QR-коду", icon = R.drawable.ic_device_desktop) { onOpen("qr") }
            SettingsRow("Сменить пароль", icon = R.drawable.ic_lock_closed, divider = false) { onOpen("change-password") }
        }
        SettingsSection {
            SettingsRow("Удалить аккаунт", icon = R.drawable.ic_trash, destructive = true, divider = false) { onOpen("delete-account") }
        }
    }
}

private val accessOptions = listOf("everyone" to "Все", "mutual" to "Взаимные", "followers" to "Подписчики", "nobody" to "Никто")

@Composable
fun PrivacySettingsScreen(container: AppContainer, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var settings by remember { mutableStateOf<PrivacySettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            settings = container.usersApi.privacy()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Ошибка"
        }
    }
    fun save(updated: PrivacySettings) {
        val previous = settings
        settings = updated
        scope.launch {
            try {
                settings = container.usersApi.updatePrivacy(
                    UpdatePrivacyRequest(updated.wallAccess, updated.likesVisibility, updated.messageAccess, updated.showLastSeen),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                settings = previous
                error = e.message ?: "Ошибка"
            }
        }
    }
    SettingsPage("Приватность", onBack) {
        val current = settings
        if (current != null) {
            SettingsSection("Основные") {
                SettingsSwitchRow("Онлайн-статус", "Показывать время последнего визита", current.showLastSeen, divider = false) {
                    save(current.copy(showLastSeen = it))
                }
            }
            SettingsSection("Посты") {
                Text("Стена", Modifier.padding(start = 16.dp, top = 12.dp), fontWeight = FontWeight.SemiBold)
                Text("Кто может писать на вашей стене", Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                accessOptions.forEachIndexed { index, (key, label) ->
                    SettingsOptionRow(label, current.wallAccess == key, index < accessOptions.lastIndex) { save(current.copy(wallAccess = key)) }
                }
            }
            SettingsSection {
                Text("Лайки", Modifier.padding(start = 16.dp, top = 12.dp), fontWeight = FontWeight.SemiBold)
                Text("Кто может видеть ваши лайкнутые посты", Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                accessOptions.forEachIndexed { index, (key, label) ->
                    SettingsOptionRow(label, current.likesVisibility == key, index < accessOptions.lastIndex) { save(current.copy(likesVisibility = key)) }
                }
            }
            SettingsSection("Пользователи") {
                SettingsRow("Чёрный список", subtitle = "Заблокированные пользователи", icon = R.drawable.ic_block, divider = false) { onOpen("blocked") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun NotificationSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    var settings by remember { mutableStateOf<NotificationSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            settings = container.notificationsApi.settings()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Ошибка"
        }
    }
    fun save(updated: NotificationSettings) {
        val previous = settings
        settings = updated
        scope.launch {
            try {
                settings = container.notificationsApi.updateSettings(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                settings = previous
                error = e.message ?: "Ошибка"
            }
        }
    }
    SettingsPage("Уведомления", onBack) {
        val current = settings
        if (current != null) {
            SettingsSection {
                SettingsSwitchRow("Уведомления", "Все уведомления разом", current.enabled) { save(current.copy(enabled = it)) }
                SettingsSwitchRow("Уведомления со звуком", "Звук при получении", current.sound, enabled = current.enabled, divider = false) {
                    save(current.copy(sound = it))
                }
            }
            SettingsSection {
                SettingsSwitchRow("Подписки", "Подписки и запросы в друзья", current.follows, current.enabled) { save(current.copy(follows = it)) }
                SettingsSwitchRow("Посты на стене", "Новые записи на вашей стене", current.wallPosts, current.enabled) { save(current.copy(wallPosts = it)) }
                SettingsSwitchRow("Лайки и реакции", "Реакции на посты и комментарии", current.likes, current.enabled) { save(current.copy(likes = it)) }
                SettingsSwitchRow("Комментарии", "Ответы на ваши комментарии", current.comments, current.enabled) { save(current.copy(comments = it)) }
                SettingsSwitchRow("Упоминания", "Упоминания в постах и комментариях", current.mentions, current.enabled, divider = false) {
                    save(current.copy(mentions = it))
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun ChangePasswordScreen(container: AppContainer, onBack: () -> Unit) {
    var oldPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canSave = oldPassword.isNotBlank() && newPassword.isNotBlank() && confirmation.isNotBlank() && !loading
    SettingsPage("Сменить пароль", onBack, action = "Сохранить", actionEnabled = canSave, onAction = {
        error = com.itd.app.ui.screens.auth.passwordError(newPassword) ?: if (newPassword != confirmation) "Пароли не совпадают" else null
        if (error == null) {
            scope.launch {
                loading = true
                try {
                    container.authApi.changePassword(ChangePasswordRequest(oldPassword, newPassword))
                    onBack()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    error = if (e.status == 401 || e.code == "INVALID_PASSWORD") "Неверный текущий пароль" else e.message
                } catch (e: Exception) {
                    error = "Ошибка сети. Попробуйте позже"
                } finally {
                    loading = false
                }
            }
        }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ItdTextField(oldPassword, { oldPassword = it }, "Текущий пароль", password = true)
            ItdTextField(newPassword, { newPassword = it }, "Новый пароль", password = true)
            ItdTextField(confirmation, { confirmation = it }, "Подтвердите пароль", password = true)
            Text("Пароль: 8-100 символов, минимум 1 буква и 1 цифра", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun DeleteAccountScreen(container: AppContainer, onBack: () -> Unit, onDeleted: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    SettingsPage("Удалить аккаунт", onBack) {
        Text("Что произойдёт при удалении:", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        SettingsSection {
            listOf(
                "Профиль будет скрыт",
                "Публикации, комментарии и лайки будут скрыты",
                "Подписки и уведомления будут удалены",
                "30 дней на восстановление",
            ).forEachIndexed { index, line -> SettingsRow(line, showChevron = false, divider = index < 3) }
        }
        Button(
            onClick = { confirm = true },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("Продолжить") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Аккаунт будет удалён") },
            text = { Text("У вас будет 30 дней, чтобы восстановить его, войдя снова.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        loading = true
                        try {
                            container.usersApi.deleteAccount()
                            onDeleted()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Произошла ошибка"
                        } finally {
                            loading = false
                        }
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}
