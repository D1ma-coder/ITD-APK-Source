package com.itd.app.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.itd.app.R
import com.itd.app.data.model.BlockedUser
import com.itd.app.data.model.PaymentMethod
import com.itd.app.data.model.Session
import com.itd.app.di.AppContainer
import com.itd.app.ui.components.UserAvatar
import com.itd.app.util.TimeFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val methodTitles = mapOf(
    "tinkoff_bank" to "T-Pay",
    "sber_loan" to "Кредит от СберБанка",
    "sber_bnpl" to "Плати частями",
    "cash" to "Наличные",
    "sbp" to "СБП",
    "yoo_money" to "ЮMoney",
    "mobile_balance" to "Баланс телефона",
    "sberbank" to "SberPay",
    "bank_card" to "Карта",
)

private val methodDescriptions = mapOf(
    "tinkoff_bank" to "T-Pay",
    "sber_loan" to "Покупки в кредит",
    "sber_bnpl" to "Рассрочка",
    "cash" to "Наличные",
    "sbp" to "Система быстрых платежей",
    "yoo_money" to "Электронный кошелёк",
    "mobile_balance" to "Оплата с телефона",
    "sberbank" to "SberPay",
    "bank_card" to "Банковская карта",
)

fun paymentMethodTitle(method: PaymentMethod): String {
    val base = methodTitles[method.type] ?: method.type
    return if (method.type == "bank_card" && method.cardLast4 != null) "${method.cardBrand ?: base} •••• ${method.cardLast4}" else base
}

private val expiryFormat = DateTimeFormatter.ofPattern("d MMMM yyyy 'г.'", Locale("ru"))

@Composable
fun SubscriptionScreen(container: AppContainer, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val auth by container.authRepository.authState.collectAsState()
    val subscription = auth?.user?.subscription
    var autoRenewal by remember(subscription) { mutableStateOf(subscription?.autoRenewal == true) }
    var confirmDisable by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun setAutoRenewal(enabled: Boolean) {
        busy = true
        scope.launch {
            try {
                val response = container.subscriptionApi.setAutoRenewal(enabled)
                if (response.error != null) error = response.error else autoRenewal = response.autoRenewal ?: enabled
                container.authRepository.loadProfile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Ошибка изменения автопродления"
            } finally {
                busy = false
            }
        }
    }
    SettingsPage("Подписка", onBack) {
        SettingsSection {
            val expires = subscription?.expiresAt?.let { runCatching { TimeFormat.parse(it) }.getOrNull() }
            val active = subscription?.isActive == true && expires != null
            Column(Modifier.padding(16.dp)) {
                Text("ИТД НУКСТА", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (active) {
                    val days = Duration.between(Instant.now(), expires).toDays().coerceAtLeast(0)
                    Text(
                        "до ${expiryFormat.format(expires!!.atZone(ZoneId.systemDefault()))} ($days дн.)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            SettingsSwitchRow("Автопродление", checked = autoRenewal, enabled = active && !busy, divider = false) { enabled ->
                if (enabled) setAutoRenewal(true) else confirmDisable = true
            }
        }
        SettingsSection {
            SettingsRow("Методы оплаты", icon = R.drawable.ic_card, divider = false) { onOpen("payment-methods") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Отключить автопродление?") },
            text = { Text("Без автопродления подписка не будет продлена после окончания текущего периода. Вы потеряете доступ к преимуществам ИТД НУКСТА.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    setAutoRenewal(false)
                }) { Text("Отключить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Оставить") } },
        )
    }
}

@Composable
fun PaymentMethodsScreen(container: AppContainer, onBack: () -> Unit) {
    var methods by remember { mutableStateOf<List<PaymentMethod>?>(null) }
    var deleting by remember { mutableStateOf<PaymentMethod?>(null) }
    var errorDialog by remember { mutableStateOf<String?>(null) }
    var bindUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun reload() {
        try {
            methods = container.subscriptionApi.paymentMethods().data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            methods = methods ?: emptyList()
            errorDialog = "Ошибка загрузки методов оплаты"
        }
    }
    LaunchedEffect(Unit) { reload() }
    SettingsPage("Методы оплаты", onBack) {
        val list = methods
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text(
                "Нет привязанных методов оплаты",
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            else -> SettingsSection {
                list.forEachIndexed { index, method ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !method.isDefault) {
                            scope.launch {
                                try {
                                    val response = container.subscriptionApi.setDefault(method.id)
                                    if (response.error != null) errorDialog = response.error
                                    reload()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    errorDialog = "Ошибка изменения метода оплаты"
                                }
                            }
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_card), null, Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(paymentMethodTitle(method), fontSize = 15.sp)
                            Text(methodDescriptions[method.type] ?: method.type, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (method.isDefault) {
                            Text(
                                "основной",
                                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                            )
                        }
                        Box(Modifier.size(36.dp).clip(CircleShape).clickable { deleting = method }, contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_trash), "Удалить", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (index < list.lastIndex) HorizontalDivider(Modifier.padding(start = 50.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
        }
        SettingsSection {
            SettingsRow("Добавить способ оплаты", icon = R.drawable.ic_add, divider = false) {
                scope.launch {
                    try {
                        val response = container.subscriptionApi.bindCard()
                        if (response.confirmationUrl != null) bindUrl = response.confirmationUrl else errorDialog = response.error ?: "Ошибка привязки карты"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorDialog = "Ошибка привязки карты"
                    }
                }
            }
        }
    }
    deleting?.let { method ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить метод оплаты?") },
            text = { Text("Метод ${paymentMethodTitle(method)} будет удалён. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        try {
                            val response = container.subscriptionApi.deleteMethod(method.id)
                            if (response.error != null) errorDialog = response.error
                            reload()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorDialog = "Ошибка удаления метода оплаты"
                        }
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
    errorDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text("Ошибка") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { errorDialog = null }) { Text("OK") } },
        )
    }
    bindUrl?.let { url ->
        PaymentConfirmationDialog(url) {
            bindUrl = null
            scope.launch { reload() }
        }
    }
}

/** Bank confirmation page; returns once the bank redirects back to the subscription settings. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PaymentConfirmationDialog(url: String, onFinished: () -> Unit) {
    Dialog(onDismissRequest = onFinished, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Методы оплаты", Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onFinished) { Text("Закрыть") }
            }
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            if (request?.url?.toString()?.contains("/settings/subscription") == true) {
                                onFinished()
                                return true
                            }
                            return false
                        }
                    }
                    loadUrl(url)
                }
            })
        }
    }
}

@Composable
fun BlockedUsersScreen(container: AppContainer, onBack: () -> Unit) {
    var users by remember { mutableStateOf<List<BlockedUser>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        users = try {
            container.usersApi.blocked().data.users
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    SettingsPage("Заблокированные", onBack) {
        val list = users
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text(
                "Нет заблокированных пользователей",
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            else -> SettingsSection {
                list.forEachIndexed { index, user ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(user.avatar, user.displayName)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName, fontWeight = FontWeight.SemiBold)
                            Text("@${user.username}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    container.usersApi.unblock(user.id)
                                    users = users?.filterNot { it.id == user.id }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                }
                            }
                        }) { Text("Разблокировать") }
                    }
                    if (index < list.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

private val appleDevices = mapOf(
    "iPhone17,1" to "iPhone 16 Pro", "iPhone17,2" to "iPhone 16 Pro Max", "iPhone17,3" to "iPhone 16", "iPhone17,4" to "iPhone 16 Plus",
    "iPhone15,4" to "iPhone 15", "iPhone15,5" to "iPhone 15 Plus", "iPhone16,1" to "iPhone 15 Pro", "iPhone16,2" to "iPhone 15 Pro Max",
    "iPhone14,7" to "iPhone 14", "iPhone14,8" to "iPhone 14 Plus", "iPhone15,2" to "iPhone 14 Pro", "iPhone15,3" to "iPhone 14 Pro Max",
    "iPhone14,4" to "iPhone 13 mini", "iPhone14,5" to "iPhone 13", "iPhone14,2" to "iPhone 13 Pro", "iPhone14,3" to "iPhone 13 Pro Max",
    "iPhone13,1" to "iPhone 12 mini", "iPhone13,2" to "iPhone 12", "iPhone13,3" to "iPhone 12 Pro", "iPhone13,4" to "iPhone 12 Pro Max",
    "iPhone12,1" to "iPhone 11", "iPhone12,3" to "iPhone 11 Pro", "iPhone12,5" to "iPhone 11 Pro Max",
    "iPhone8,4" to "iPhone SE (1-е пок.)", "iPhone12,8" to "iPhone SE (2-е пок.)", "iPhone14,6" to "iPhone SE (3-е пок.)",
    "iPhone11,2" to "iPhone XS", "iPhone11,4" to "iPhone XS Max", "iPhone11,6" to "iPhone XS Max", "iPhone11,8" to "iPhone XR",
    "iPhone10,3" to "iPhone X", "iPhone10,6" to "iPhone X",
    "iPad14,3" to "iPad Pro 11”", "iPad14,4" to "iPad Pro 11”", "iPad14,5" to "iPad Pro 12.9”", "iPad14,6" to "iPad Pro 12.9”",
    "iPad13,4" to "iPad Pro 11” (M1)", "iPad13,5" to "iPad Pro 11” (M1)", "iPad13,6" to "iPad Pro 11” (M1)", "iPad13,7" to "iPad Pro 11” (M1)",
    "iPad13,8" to "iPad Pro 12.9” (M1)", "iPad13,9" to "iPad Pro 12.9” (M1)", "iPad13,10" to "iPad Pro 12.9” (M1)", "iPad13,11" to "iPad Pro 12.9” (M1)",
    "iPad16,3" to "iPad Pro 11” (M4)", "iPad16,4" to "iPad Pro 11” (M4)", "iPad16,5" to "iPad Pro 13” (M4)", "iPad16,6" to "iPad Pro 13” (M4)",
    "iPad13,1" to "iPad Air (4-е пок.)", "iPad13,2" to "iPad Air (4-е пок.)", "iPad13,16" to "iPad Air (5-е пок.)", "iPad13,17" to "iPad Air (5-е пок.)",
    "iPad14,8" to "iPad Air 11” (M2)", "iPad14,9" to "iPad Air 11” (M2)", "iPad14,10" to "iPad Air 13” (M2)", "iPad14,11" to "iPad Air 13” (M2)",
    "iPad14,1" to "iPad mini (6-е пок.)", "iPad14,2" to "iPad mini (6-е пок.)", "iPad16,1" to "iPad mini (A17 Pro)", "iPad16,2" to "iPad mini (A17 Pro)",
    "iPad12,1" to "iPad (9-е пок.)", "iPad12,2" to "iPad (9-е пок.)", "iPad13,18" to "iPad (10-е пок.)", "iPad13,19" to "iPad (10-е пок.)",
    "iPad15,7" to "iPad (A16)", "iPad15,8" to "iPad (A16)",
)

private fun sessionIcon(session: Session): Int = when {
    session.clientName == "ITD Android" || session.clientName == "ITD iOS" -> R.drawable.ic_device_phone
    session.osName == "macOS" || session.osName == "Windows" || session.osName == "Linux" -> R.drawable.ic_device_laptop
    session.deviceType == "tablet" -> R.drawable.ic_device_tablet
    session.deviceType == "mobile" -> R.drawable.ic_device_phone
    else -> R.drawable.ic_device_desktop
}

private fun sessionTitle(session: Session): String =
    session.deviceModel?.let { appleDevices[it] ?: it } ?: session.clientName ?: "Неизвестное устройство"

private fun sessionClient(session: Session): String? {
    val parts = ArrayList<String>()
    if (session.deviceModel != null) {
        val client = session.clientName
        if (client != null) parts.add(listOfNotNull(client, session.clientVersion).joinToString(" "))
    } else {
        session.clientVersion?.let(parts::add)
    }
    when {
        session.osName == "macOS" -> parts.add("macOS")
        session.osName != null -> parts.add(listOfNotNull(session.osName, session.osVersion).joinToString(" "))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun sessionPlace(session: Session): String {
    val location = listOfNotNull(session.ipCity, session.ipCountry).joinToString(", ")
    return listOf(location, TimeFormat.relative(session.lastUsedAt)).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun revokedText(count: Int): String = when {
    count == 0 -> "Других активных сессий не было"
    else -> "$count " + TimeFormat.plural(count, "другая сессия завершена", "других сессии завершено", "других сессий завершено")
}

@Composable
fun SessionsScreen(container: AppContainer, onBack: () -> Unit) {
    var sessions by remember { mutableStateOf<List<Session>?>(null) }
    var revoking by remember { mutableStateOf<Session?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun reload() {
        try {
            sessions = container.authApi.sessions().sessions.sortedByDescending { it.isCurrent }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Ошибка"
            sessions = sessions ?: emptyList()
        }
    }
    LaunchedEffect(Unit) { reload() }
    SettingsPage("Активные сессии", onBack) {
        val list = sessions
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            list.firstOrNull { it.isCurrent }?.let { current ->
                SettingsSection("Это устройство") { SessionRow(current, null) }
            }
            val others = list.filterNot { it.isCurrent }
            if (others.isNotEmpty()) {
                SettingsSection {
                    SettingsRow(
                        if (busy) "Завершение..." else "Завершить другие сеансы",
                        subtitle = "Выйти на всех устройствах, кроме текущего.",
                        destructive = true,
                        divider = false,
                    ) { if (!busy) confirmAll = true }
                }
                SettingsSection {
                    others.forEachIndexed { index, session ->
                        SessionRow(session) { revoking = session }
                        if (index < others.lastIndex) HorizontalDivider(Modifier.padding(start = 50.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        }
    }
    revoking?.let { session ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text("Завершить сессию?") },
            text = { Text("На этом устройстве потребуется войти заново.") },
            confirmButton = {
                TextButton(onClick = {
                    revoking = null
                    scope.launch {
                        try {
                            container.authApi.revokeSession(session.id)
                            reload()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Ошибка"
                        }
                    }
                }) { Text("Завершить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text("Отмена") } },
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Завершить другие сеансы?") },
            text = { Text("На остальных устройствах потребуется войти заново. Текущая сессия не будет затронута.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    busy = true
                    scope.launch {
                        try {
                            message = revokedText(container.authApi.revokeAllSessions().revokedCount)
                            reload()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Ошибка"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Завершить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Отмена") } },
        )
    }
    error?.let { text ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Ошибка") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun SessionRow(session: Session, onRevoke: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(sessionIcon(session)), null, Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(sessionTitle(session), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            sessionClient(session)?.let { Text(it, Modifier.padding(top = 2.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            Text(sessionPlace(session), Modifier.padding(top = 2.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1)
        }
        if (onRevoke != null) TextButton(onClick = onRevoke) { Text("Завершить", color = MaterialTheme.colorScheme.error) }
    }
}
