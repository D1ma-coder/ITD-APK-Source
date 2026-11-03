package com.itd.app.ui.screens.qr

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TabletMac
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.itd.app.data.model.LocationPrecision
import com.itd.app.data.model.QrLoginTarget
import java.util.Locale

private enum class QrDecision { APPROVED, REJECTED }

private val Glass = Color.White.copy(alpha = 0.12f)
private val Warning = Color(0xFFFF9800)
private val decisionSpec: FiniteAnimationSpec<Float> = tween(420)

/** The sign-in request in the sheet: device glyph, title, where it comes from, and the decision buttons. */
@Composable
fun QrLoginContent(
    target: QrLoginTarget,
    foreignCountry: Boolean,
    state: QrLoginState,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val decision = when (state) {
        QrLoginState.Approved -> QrDecision.APPROVED
        QrLoginState.Rejected -> QrDecision.REJECTED
        else -> null
    }
    Column(modifier.fillMaxWidth().animateContentSize(spring(stiffness = 400f))) {
        DeviceGlyph(target, decision)
        Spacer(Modifier.height(14.dp))
        val headline = when (decision) {
            QrDecision.APPROVED -> "Вход разрешён"
            QrDecision.REJECTED -> "Вход отклонён"
            null -> "Вход в аккаунт"
        }
        AnimatedContent(headline, transitionSpec = { fadeIn(decisionSpec) togetherWith fadeOut(decisionSpec) }, label = "qr-headline") { text ->
            Text(
                text, Modifier.fillMaxWidth().padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
        }
        AnimatedContent(decision, transitionSpec = { fadeIn(decisionSpec) togetherWith fadeOut(decisionSpec) }, label = "qr-subtitle") { value ->
            if (value == null) {
                RequestOrigin(target)
            } else {
                Text(
                    if (value == QrDecision.APPROVED) "Устройство войдёт в аккаунт через пару секунд. Оно появится в списке активных сессий."
                    else "Код уничтожен, войти по нему больше нельзя. Если это были не вы — стоит сменить пароль.",
                    Modifier.fillMaxWidth().padding(start = 28.dp, top = 8.dp, end = 28.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
            }
        }
        AnimatedVisibility(decision == null, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
            Column {
                if (foreignCountry) ForeignCountryWarning(Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp))
                RequestDetails(target, Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        DecisionButtons(state, decision, onApprove, onReject, onClose, Modifier.padding(horizontal = 20.dp))
    }
}

/** 68dp circle with the device icon; a check that bounces in with a ring on approval, an orange hand on rejection. */
@Composable
private fun DeviceGlyph(target: QrLoginTarget, decision: QrDecision?) {
    var celebrated by remember { mutableStateOf(false) }
    LaunchedEffect(decision) { if (decision == QrDecision.APPROVED) celebrated = true }
    val bounce by animateFloatAsState(if (decision == QrDecision.APPROVED && !celebrated) 1f else 0f, spring(0.45f, 1500f), label = "")
    val ring by animateFloatAsState(if (celebrated) 1f else 0f, tween(550), label = "")
    Box(Modifier.fillMaxWidth().padding(top = 26.dp), contentAlignment = Alignment.Center) {
        if (decision == QrDecision.APPROVED) {
            Box(
                Modifier.size(68.dp).scale(ring * 0.75f + 1f).alpha((1f - ring) * 0.9f)
                    .drawBehind { drawCircle(Color.White.copy(alpha = 0.5f), style = Stroke(2f)) },
            )
        }
        Box(Modifier.size(68.dp).scale(bounce * 0.16f + 1f).clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) {
            AnimatedContent(
                decision,
                transitionSpec = { (fadeIn(decisionSpec) + scaleIn(decisionSpec, 0.7f)) togetherWith (fadeOut(decisionSpec) + scaleOut(decisionSpec, 0.7f)) },
                label = "qr-glyph",
            ) { value ->
                when (value) {
                    QrDecision.APPROVED -> Icon(Icons.Filled.Check, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurface)
                    QrDecision.REJECTED -> Icon(Icons.Filled.PanTool, null, Modifier.size(30.dp), tint = Warning)
                    null -> Icon(deviceIcon(target), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun deviceIcon(target: QrLoginTarget): ImageVector = when (target.os) {
    "Windows" -> Icons.Outlined.Computer
    "iOS" -> Icons.Outlined.PhoneAndroid
    "Linux", "macOS" -> Icons.Outlined.Laptop
    else -> when (target.deviceType) {
        "mobile" -> Icons.Outlined.PhoneAndroid
        "tablet" -> Icons.Outlined.TabletMac
        else -> Icons.Outlined.Computer
    }
}

/** Flag and "Город, Страна", then "Браузер · ОС". */
@Composable
private fun RequestOrigin(target: QrLoginTarget) {
    Column(Modifier.fillMaxWidth()) {
        target.locationTitle?.let { location ->
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, top = 6.dp, end = 24.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                CountryFlag(target.ipCountry)
                Spacer(Modifier.size(8.dp))
                Text(location, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            listOfNotNull(target.clientTitle, target.osTitle).joinToString(" · "),
            Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp, end = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center,
        )
    }
}

/** Flag of a two-letter country code from the bundled assets, if there is one. */
@Composable
fun CountryFlag(country: String?, modifier: Modifier = Modifier) {
    val code = country?.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: return
    val context = LocalContext.current
    val exists = remember(code) {
        try {
            context.assets.open("flags/$code.svg").close()
            true
        } catch (_: Throwable) {
            false
        }
    }
    if (!exists) return
    val shape = RoundedCornerShape(4.4.dp)
    AsyncImage(
        ImageRequest.Builder(context).data("file:///android_asset/flags/$code.svg").crossfade(false).build(), null,
        modifier.height(20.dp).aspectRatio(4f / 3f).clip(shape).border(0.5.dp, Color.White.copy(alpha = 0.15f), shape),
        contentScale = ContentScale.FillHeight,
    )
}

@Composable
private fun ForeignCountryWarning(modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Warning.copy(alpha = 0.16f)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Warning, null, Modifier.padding(top = 1.dp).size(16.dp), tint = Warning)
        Spacer(Modifier.size(10.dp))
        Text("Вход из другой страны, чем та, где сейчас это устройство. Если это не вы — откажите.", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

/** IP address and the approximate location on a map. */
@Composable
private fun RequestDetails(target: QrLoginTarget, modifier: Modifier) {
    if (target.ipAddress == null && target.latitude == null) return
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Glass)) {
        target.ipAddress?.let { ip ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("IP-адрес", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                Text(ip, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 1)
            }
        }
        RequestMap(target)
    }
}

@Composable
private fun RequestMap(target: QrLoginTarget, height: Float = 132f) {
    val latitude = target.latitude ?: return
    val longitude = target.longitude ?: return
    val accuracy = target.accuracyKm?.takeIf { it > 0.0 } ?: return
    Box(Modifier.fillMaxWidth().height(height.dp)) {
        QrLocationMap(latitude, longitude, accuracy, Modifier.fillMaxWidth().height(height.dp).alpha(0.55f))
        Box(Modifier.fillMaxWidth().height(height.dp).background(Color.White.copy(alpha = 0.1f)))
        if (target.locationPrecision == LocationPrecision.CITY) CityRing(height)
    }
}

@Composable
private fun BoxScope.CityRing(height: Float) {
    val blue = Color(0xFF2979FF)
    Box(
        Modifier.align(Alignment.Center).size(maxOf(height - 44f, 8f).dp).drawBehind {
            drawCircle(blue.copy(alpha = 0.18f))
            drawCircle(blue.copy(alpha = 0.55f), style = Stroke(2f))
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(blue))
    }
}

/** "Войти" / "Готово" and, until decided, "Это не я"; spinners while the answer is sent. */
@Composable
private fun DecisionButtons(
    state: QrLoginState,
    decision: QrDecision?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val busy = state is QrLoginState.Approving || state is QrLoginState.Rejecting
    Column(modifier.fillMaxWidth()) {
        PrimaryButton(if (decision == null) onApprove else onClose, enabled = !busy) {
            if (state is QrLoginState.Approving) {
                CircularProgressIndicator(Modifier.size(20.dp), MaterialTheme.colorScheme.surface, 2.dp)
            } else {
                Text(
                    if (decision == null) "Войти" else "Готово", color = MaterialTheme.colorScheme.surface,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        AnimatedVisibility(decision == null, enter = fadeIn(decisionSpec), exit = fadeOut(decisionSpec)) {
            Column {
                Spacer(Modifier.height(10.dp))
                SecondaryButton(onReject, enabled = !busy) {
                    if (state is QrLoginState.Rejecting) {
                        CircularProgressIndicator(Modifier.size(20.dp), MaterialTheme.colorScheme.onSurface, 2.dp)
                    } else {
                        Text("Это не я", color = Color(0xFFFF453A), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(onClick: () -> Unit, enabled: Boolean, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center, content = content,
    )
}

@Composable
private fun SecondaryButton(onClick: () -> Unit, enabled: Boolean, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(Glass)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center, content = content,
    )
}
