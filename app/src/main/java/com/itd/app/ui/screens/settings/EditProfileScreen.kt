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
import com.itd.app.ui.components.PinIcon
import com.itd.app.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import com.itd.app.data.model.UpdateProfileRequest
import com.itd.app.data.model.UserPin
import com.itd.app.data.model.UserProfile
import com.itd.app.di.AppContainer
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
    val changed = profile?.let { name.trim() != it.displayName || handle != it.username.orEmpty() || bio.trim() != it.bio.orEmpty().trim() || activePin != it.pin?.slug } ?: false
    SettingsPage(
        "Профиль",
        onBack,
        action = "Сохранить",
        actionEnabled = canSave && changed,
        actionBusy = saving,
        close = true,
        contentPadding = 0.dp,
        spacing = 0.dp,
        onAction = {
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
        },
    ) {
        val current = profile ?: return@SettingsPage
        val colors = MaterialTheme.colorScheme
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(800f / 267f).clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant)
                        .clickable(onClick = onEditBanner),
                ) {
                    current.banner?.let { AsyncImage(it, "Banner", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_pencil_edit), "Редактировать баннер", Modifier.size(18.dp), tint = Color.White)
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomCenter).size(72.dp).clip(CircleShape).background(colors.surfaceVariant.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(current.avatar ?: "?", fontSize = 34.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Эмоджи-клан", color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Выбран при регистрации. Изменить нельзя", color = colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        ProfileField("Имя", name, { name = it.take(50) }, "Введите имя")
        Spacer(Modifier.height(20.dp))
        ProfileField("Username", handle, { handle = it.trim().take(50) }, "username", prefix = "@")
        val hint = when {
            handle.isNotEmpty() && handle.length < 3 -> "Минимум 3 символа" to colors.error
            handle.isNotEmpty() && !usernamePattern.matches(handle) -> "Только буквы, цифры и _ (3-50 символов)" to colors.error
            available == false -> "Этот юзернейм уже занят" to colors.error
            available == true && handle != current.username -> "$handle доступен" to Color(0xFF34C759)
            else -> "Ваш уникальный идентификатор (только латиница, цифры и _)" to colors.onSurfaceVariant
        }
        Text(hint.first, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), color = hint.second, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        ProfileField("О себе", bio, { bio = it.take(BIO_LIMIT) }, "Расскажите немного о себе", minHeight = 100.dp, singleLine = false)
        Text(
            "${bio.length}/$BIO_LIMIT",
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Пин", Modifier.padding(bottom = 2.dp), color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Отображается рядом с именем", Modifier.padding(bottom = 8.dp), color = colors.onSurfaceVariant, fontSize = 12.sp)
            if (pins.isEmpty()) {
                Text("У вас пока нет пинов", Modifier.fillMaxWidth().padding(vertical = 12.dp), color = colors.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
            } else {
                pins.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { pin ->
                            val selected = pin.slug == activePin
                            val shape = RoundedCornerShape(12.dp)
                            Column(
                                Modifier.weight(1f).fillMaxHeight().clip(shape).background(colors.surfaceVariant)
                                    .let { if (selected) it.border(2.dp, colors.primary, shape) else it }
                                    .clickable { activePin = if (selected) null else pin.slug }
                                    .padding(horizontal = 12.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PinIcon(pin, 40.dp)
                                Spacer(Modifier.height(8.dp))
                                Text(pin.name, color = colors.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = colors.error) }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    prefix: String? = null,
    minHeight: Dp = 44.dp,
    singleLine: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(label, Modifier.padding(bottom = 6.dp), color = colors.onSurface, fontSize = 14.sp)
        BasicTextField(
            value,
            onChange,
            Modifier.fillMaxWidth(),
            singleLine = singleLine,
            textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (prefix != null) {
                        Text(prefix, color = colors.onSurfaceVariant, fontSize = 15.sp)
                        Spacer(Modifier.width(2.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, color = colors.onSurfaceVariant, fontSize = 15.sp, lineHeight = 20.sp)
                        inner()
                    }
                }
            },
        )
    }
}
