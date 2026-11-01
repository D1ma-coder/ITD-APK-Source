package com.itd.app.ui.screens.account

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itd.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AccountStatusScreen(container: AppContainer, canRestore: Boolean, deadline: String?, onLogout: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (canRestore) "Аккаунт удалён" else "Аккаунт деактивирован", style = MaterialTheme.typography.headlineSmall)
        deadline?.takeIf { canRestore }?.let { value ->
            val date = runCatching {
                java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ru"))
                    .format(com.itd.app.util.TimeFormat.parse(value).atZone(java.time.ZoneId.systemDefault()))
            }.getOrDefault(value)
            Text("Вы можете восстановить аккаунт до $date", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (!canRestore && deadline != null) Text("Срок восстановления истёк")
        if (canRestore) Button(onClick = { scope.launch {
            try { container.usersApi.restoreAccount(); container.authRepository.loadProfile() }
            catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { error = failure.message }
        } }) { Text("Восстановить аккаунт") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onLogout) { Text("Выйти") }
    }
}
