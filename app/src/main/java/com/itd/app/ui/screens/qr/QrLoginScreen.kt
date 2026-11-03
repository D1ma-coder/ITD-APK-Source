package com.itd.app.ui.screens.qr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itd.app.R
import com.itd.app.data.model.QrLoginTarget
import com.itd.app.ui.components.glassRim
import java.util.Locale

private fun deviceCountry(context: Context): String {
    val network = context.getSystemService(TelephonyManager::class.java)?.networkCountryIso
    return (network?.takeIf { it.isNotEmpty() } ?: Locale.getDefault().country).uppercase(Locale.ROOT)
}

/** "Подключить устройство": scan the login QR code of the website, then approve or reject the sign-in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrLoginScreen(viewModel: QrLoginViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.deviceCountry = deviceCountry(context) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val invalid by viewModel.invalidCode.collectAsStateWithLifecycle()
    val invalidLink by viewModel.invalidLink.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    var rect by remember { mutableStateOf<Rect?>(null) }
    var lastRect by remember { mutableStateOf<Rect?>(null) }
    var target by remember { mutableStateOf<QrLoginTarget?>(null) }
    LaunchedEffect(state) { (state as? QrLoginState.Confirming)?.let { target = it.target } }
    val scanning = state is QrLoginState.Scanning
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            QrScanner(
                viewModel::handleScannedCode,
                { found ->
                    rect = found
                    if (found != null) lastRect = found
                    if (found == null) viewModel.onCodeLost()
                },
                paused = !scanning,
            )
            val current = rect
            val frame = when {
                scanning -> if (current == null) QrFrame.Searching else if (invalid) QrFrame.Foreign(current) else QrFrame.Tracking(current)
                state is QrLoginState.Checking -> lastRect?.let { QrFrame.Locked(it) } ?: QrFrame.Searching
                else -> lastRect?.let { QrFrame.Covered(it) } ?: QrFrame.Searching
            }
            QrScannerOverlay(frame)
            val hint = when {
                state is QrLoginState.Checking -> "Проверяем код…"
                invalid -> "Это не код для входа в ИТД"
                else -> "Наведите камеру на код на экране компьютера"
            }
            QrHeader(hint, invalid, faded = !(scanning || state is QrLoginState.Checking))
        } else {
            NoCameraAccess { permission.launch(Manifest.permission.CAMERA) }
        }
        BackButton(onClose)
    }
    val sheetState = state
    if (!(sheetState is QrLoginState.Scanning || sheetState is QrLoginState.Checking) && (target != null || sheetState is QrLoginState.Failed)) {
        ModalBottomSheet(
            onDismissRequest = { if (sheetState is QrLoginState.Confirming || sheetState is QrLoginState.Failed) onClose() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Transparent,
            dragHandle = null,
        ) {
            Column(Modifier.padding(bottom = 8.dp)) {
                if (sheetState is QrLoginState.Failed) {
                    QrFailure(sheetState.title, sheetState.message, sheetState.retryable, viewModel::retry, onClose)
                } else {
                    target?.let { login ->
                        val foreign = viewModel.deviceCountry?.let { device -> login.ipCountry?.let { !device.equals(it, ignoreCase = true) } } == true
                        QrLoginContent(login, foreign, sheetState, viewModel::approve, viewModel::reject, onClose)
                    }
                }
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

/** Round glass back button over the camera, below the status bar. */
@Composable
private fun BackButton(onClose: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp() }.coerceAtLeast(24.dp)
    Box(
        Modifier.padding(start = 16.dp, top = top + 8.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).glassRim()
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_back), "Назад", Modifier.size(16.dp), tint = Color.White)
    }
}

/** Title, hint and the three steps over the camera; they fade while a decision is on screen. */
@Composable
private fun QrHeader(hint: String, warning: Boolean, faded: Boolean) {
    val alpha by animateFloatAsState(if (faded) 0f else 1f, tween(400), label = "")
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 68.dp, bottom = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Подключить устройство", color = Color.White.copy(alpha = alpha), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            hint, Modifier.padding(horizontal = 32.dp),
            color = if (warning) Color(0xFFFFB938).copy(alpha = alpha) else Color.White.copy(alpha = alpha * 0.75f), fontSize = 15.sp,
        )
        Spacer(Modifier.weight(1f))
        Column(Modifier.requiredWidth(300.dp).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QrStep(1, "Откройте сайт ИТД на компьютере", alpha)
            QrStep(2, "На странице входа выберите «Войти по QR-коду»", alpha)
            QrStep(3, "Наведите камеру на появившийся код", alpha)
        }
    }
}

@Composable
private fun QrStep(number: Int, text: String, alpha: Float) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f * alpha)), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = Color.Black.copy(alpha = alpha), fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White.copy(alpha = 0.8f * alpha), fontSize = 13.sp)
    }
}

@Composable
private fun NoCameraAccess(onAllow: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Нужен доступ к камере", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Без камеры код не отсканировать.", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Разрешить",
            Modifier.clip(CircleShape).background(Color.White).clickable(onClick = onAllow).padding(horizontal = 24.dp, vertical = 12.dp),
            color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Error in the sheet: title, message, "Сканировать заново" when it can be retried, then Закрыть / Понятно. */
@Composable
private fun QrFailure(title: String, message: String, retryable: Boolean, onRetry: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = colors.onSurfaceVariant, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        if (retryable) {
            Text(
                "Сканировать заново",
                Modifier.fillMaxWidth().clip(CircleShape).background(colors.onSurface).clickable(onClick = onRetry).padding(vertical = 15.dp),
                color = colors.surface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(
            if (retryable) "Закрыть" else "Понятно",
            Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClose).padding(vertical = 15.dp),
            color = colors.onSurface, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
