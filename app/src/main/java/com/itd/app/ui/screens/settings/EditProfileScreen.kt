package com.itd.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itd.app.data.model.UpdateProfileRequest
import com.itd.app.data.model.UserPin
import com.itd.app.data.model.UserProfile
import com.itd.app.di.AppContainer
import com.itd.app.ui.components.ItdTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val usernamePattern = Regex("^[a-zA-Z0-9_]{3,50}$")
private const val BIO_LIMIT = 160

@Composable
fun EditProfileScreen(container: AppContainer, username: String, onBack: () -> Unit, onEditBanner: () -> Unit = {}) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var name by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var pins by remember { mutableStateOf<List<UserPin>>(emptyList()) }
    var activePin by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(username) {
        try {
            val loaded = container.usersApi.profile(username)
            profile = loaded
            name = loaded.displayName
            handle = loaded.username.orEmpty()
            bio = loaded.bio.orEmpty()
            val pinData = container.usersApi.pins().data
            pins = pinData.pins
            activePin = pinData.activePin
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить"
        }
    }
    LaunchedEffect(handle) {
        available = null
        if (handle == profile?.username || !usernamePattern.matches(handle)) return@LaunchedEffect
        delay(400)
        available = try {
            container.usersApi.checkUsername(handle).available
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    val usernameError = when {
        handle.isNotEmpty() && handle.length < 3 -> "Минимум 3 символа"
        handle.isNotEmpty() && !usernamePattern.matches(handle) -> "Только буквы, цифры и _ (3-50 символов)"
        available == false -> "Этот юзернейм уже занят"
        else -> null
    }
    val nameError = if (profile != null && name.isBlank()) "Введите имя" else null
    val canSave = profile != null && !saving && nameError == null && usernameError == null && handle.isNotEmpty()
    SettingsPage("Редактировать профиль", onBack, action = "Сохранить", actionEnabled = canSave, onAction = {
        scope.launch {
            saving = true
            try {
                container.usersApi.updateProfile(UpdateProfileRequest(displayName = name.trim(), username = handle, bio = bio.trim()))
                val previousPin = profile?.pin?.slug
                if (activePin != previousPin) {
                    if (activePin == null) container.usersApi.removePin() else container.usersApi.setPin(activePin!!)
                }
                container.authRepository.loadProfile()
                onBack()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Произошла ошибка"
            } finally {
                saving = false
            }
        }
    }) {
        val current = profile ?: return@SettingsPage
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant).clickable(onClick = onEditBanner)) {
            current.banner?.let { AsyncImage(it, "Banner", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Text(
                "Редактировать баннер",
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 6.dp),
                color = Color.White,
                fontSize = 14.sp,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
                Text(current.avatar ?: "?", fontSize = 32.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Эмоджи-клан", fontWeight = FontWeight.SemiBold)
                Text("Выбран при регистрации. Изменить нельзя", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Имя", fontWeight = FontWeight.Medium)
            ItdTextField(name, { name = it.take(50) }, "Введите имя", error = nameError)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Username", fontWeight = FontWeight.Medium)
            ItdTextField(handle, { handle = it.trim().take(50) }, "username", error = usernameError)
            if (usernameError == null) {
                val ok = available == true && handle.length >= 3 && handle != current.username
                Text(
                    if (ok) "@$handle доступен" else "Ваш уникальный идентификатор (только латиница, цифры и _)",
                    fontSize = 12.sp,
                    color = if (ok) Color(0xFF34C759) else colors.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("О себе", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text("${bio.length}/$BIO_LIMIT", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            OutlinedTextField(
                bio,
                { bio = it.take(BIO_LIMIT) },
                Modifier.fillMaxWidth().heightIn(min = 110.dp),
                placeholder = { Text("Расскажите немного о себе") },
                shape = RoundedCornerShape(12.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Пин", fontWeight = FontWeight.Medium)
            Text("Отображается рядом с именем", fontSize = 12.sp, color = colors.onSurfaceVariant)
            if (pins.isEmpty()) {
                Text("У вас пока нет пинов", color = colors.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pins.forEach { pin ->
                        val selected = pin.slug == activePin
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, RoundedCornerShape(12.dp))
                                .clickable { activePin = if (selected) null else pin.slug }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(pin.name, fontSize = 20.sp)
                            pin.description?.let { Text(it, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1) }
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = colors.error) }
    }
}
