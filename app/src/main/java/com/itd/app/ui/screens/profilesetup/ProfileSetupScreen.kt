package com.itd.app.ui.screens.profilesetup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itd.app.R
import com.itd.app.ui.components.ItdTextField
import com.itd.app.ui.components.emojipicker.EmojiPicker

@Composable
fun ProfileSetupScreen(viewModel: ProfileSetupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Icon(painterResource(R.drawable.ic_logo), "ИТД", Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(if (state.step == ProfileSetupStep.NameUsername) "Настройка профиля" else "Выбери эмоджи", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.step == ProfileSetupStep.NameUsername) "Заполните данные профиля"
            else "Выбрав эмоджи, ты вступаешь в клан с теми,\nу кого такой же. Изменить потом нельзя!",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        when (state.step) {
            ProfileSetupStep.NameUsername -> {
                ItdTextField(state.displayName, viewModel::onDisplayNameChange, "Имя", error = state.displayNameError)
                Spacer(Modifier.height(16.dp))
                ItdTextField(state.username, viewModel::onUsernameChange, "Юзернейм", error = state.usernameError)
                if (state.isCheckingUsername) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    if (state.usernameAvailable == true) "@${state.username} доступен" else "Уникальный никнейм для твоего профиля",
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    fontSize = 12.sp,
                    color = if (state.usernameAvailable == true) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = viewModel::next, enabled = !state.isCheckingUsername, modifier = Modifier.fillMaxWidth()) { Text("Далее") }
            }
            ProfileSetupStep.EmojiAvatar -> {
                state.selectedEmoji?.let { Text(it, fontSize = 48.sp) }
                EmojiPicker(viewModel::onEmojiSelected, Modifier.weight(1f).fillMaxWidth())
                state.generalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = viewModel::createProfile, enabled = state.selectedEmoji != null && !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Создать профиль")
                }
                TextButton(onClick = viewModel::back) { Text("Назад") }
            }
        }
    }
}
