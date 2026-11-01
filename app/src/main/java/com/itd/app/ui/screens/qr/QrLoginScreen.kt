package com.itd.app.ui.screens.qr

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itd.app.R
import com.itd.app.data.model.QrLoginTarget
import java.util.Locale

private val steps = listOf(
    "Откройте сайт ИТД на компьютере",
    "На странице входа выберите «Войти по QR-коду»",
    "Наведите камеру на появившийся код",
)

private fun deviceCountry(context: Context): String {
    val network = context.getSystemService(TelephonyManager::class.java)?.networkCountryIso
    return (network?.takeIf { it.isNotEmpty() } ?: Locale.getDefault().country).uppercase(Locale.ROOT)
}

@Composable
fun QrLoginScreen(viewModel: QrLoginViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val invalid by viewModel.invalidCode.collectAsStateWithLifecycle()
    val invalidLink by viewModel.invalidLink.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_chevron_back), "Назад", Modifier.size(22.dp))
            }
            Text("Подключить устройство", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.width(44.dp))
        }
        when (val current = state) {
            QrLoginState.Scanning -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(step, fontSize = 15.sp)
                    }
                }
                QrScanner(viewModel::onCode, Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(20.dp)))
                Text(
                    if (invalid) "Это не код для входа в ИТД" else "Наведите камеру на код на экране компьютера",
                    Modifier.fillMaxWidth(),
                    color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            QrLoginState.Loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Проверяем код…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is QrLoginState.Confirmation -> ConfirmLogin(current.target, viewModel::approve, viewModel::reject)
            is QrLoginState.Error -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(current.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(current.message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (current.retryable) Button(onClick = viewModel::scanAgain, Modifier.fillMaxWidth()) { Text("Сканировать заново") }
                TextButton(onClick = onClose) { Text("Закрыть") }
            }
            is QrLoginState.Complete -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(if (current.approved) "Вход разрешён" else "Вход отклонён", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (current.approved) {
                        "Устройство войдёт в аккаунт через пару секунд. Оно появится в списке активных сессий."
                    } else {
                        "Код уничтожен, войти по нему больше нельзя. Если это были не вы — стоит сменить пароль."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onClose, Modifier.fillMaxWidth()) { Text("Понятно") }
            }
        }
    }
    if (invalidLink) {
        AlertDialog(
            onDismissRequest = viewModel::dismissInvalidLink,
            title = { Text("Ссылка не подходит") },
            text = { Text("Эта ссылка не для входа в ИТД. Откройте страницу входа на компьютере и отсканируйте код оттуда.") },
            confirmButton = { TextButton(onClick = viewModel::dismissInvalidLink) { Text("Понятно") } },
        )
    }
}

@Composable
private fun ConfirmLogin(target: QrLoginTarget, onApprove: () -> Unit, onReject: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val foreignCountry = target.ipCountry?.let { it.length == 2 && !it.equals(deviceCountry(context), ignoreCase = true) } == true
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Вход в аккаунт", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (foreignCountry) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFF9800).copy(alpha = 0.16f)).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(painterResource(R.drawable.ic_warning), null, Modifier.padding(top = 1.dp).size(16.dp), tint = Color(0xFFFF9800))
                Spacer(Modifier.width(8.dp))
                Text("Вход из другой страны, чем та, где сейчас это устройство. Если это не вы — откажите.", fontSize = 14.sp)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(listOfNotNull(target.client, target.clientVersion).joinToString(" ").ifEmpty { "Неизвестное устройство" }, fontWeight = FontWeight.SemiBold)
            listOfNotNull(target.os, target.osVersion).joinToString(" ").takeIf { it.isNotEmpty() }?.let { Text(it, color = colors.onSurfaceVariant) }
            listOfNotNull(target.ipCity, target.ipCountry).joinToString(", ").takeIf { it.isNotEmpty() }?.let { Text(it, color = colors.onSurfaceVariant) }
            target.ipAddress?.let { Text("IP-адрес · $it", color = colors.onSurfaceVariant, fontSize = 13.sp) }
        }
        if (target.latitude != null && target.longitude != null && (target.accuracyKm ?: 0.0) > 0.0) {
            QrLocationMap(target.latitude, target.longitude, target.accuracyKm!!, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)))
        }
        Button(onClick = onApprove, Modifier.fillMaxWidth().height(50.dp)) { Text("Войти", fontWeight = FontWeight.SemiBold) }
        OutlinedButton(onClick = onReject, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Это не я", color = Color(0xFFFF453A), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}
