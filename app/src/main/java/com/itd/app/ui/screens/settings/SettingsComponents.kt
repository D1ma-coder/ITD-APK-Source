package com.itd.app.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.R
import androidx.compose.foundation.layout.height
import com.itd.app.ui.theme.ItdBlue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState

/** Settings page scaffold: round back button, centred title, optional pill action and scrollable content. */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    action: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
    scrollable: Boolean = true,
    close: Boolean = false,
    actionBusy: Boolean = false,
    contentPadding: Dp = 16.dp,
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsHeader(title, onBack, action, actionEnabled, onAction, close, actionBusy)
        val body = Modifier.fillMaxWidth().weight(1f).let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
        Column(body.padding(horizontal = contentPadding).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            content()
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
fun SettingsHeader(
    title: String,
    onBack: () -> Unit,
    action: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
    close: Boolean = false,
    actionBusy: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 20.dp)) {
        Text(title, Modifier.align(Alignment.Center), colors.onSurface, 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Box(
            Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = 0.1f)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(if (close) R.drawable.ic_close else R.drawable.ic_chevron_back), if (close) "Закрыть" else "Назад", Modifier.size(22.dp), tint = colors.onSurface)
        }
        if (action != null) {
            val active = actionEnabled && !actionBusy
            val background by animateColorAsState(if (active) ItdBlue else colors.onSurface.copy(alpha = 0.1f), tween(220), label = "settings-header-action-bg")
            val foreground by animateColorAsState(if (active) Color.White else colors.onSurfaceVariant.copy(alpha = 0.4f), tween(220), label = "settings-header-action-fg")
            Box(
                Modifier.align(Alignment.CenterEnd).height(44.dp).clip(CircleShape).background(background)
                    .clickable(enabled = active, onClick = onAction).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (actionBusy) CircularProgressIndicator(Modifier.size(18.dp), color = foreground, strokeWidth = 2.dp)
                else Text(action, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) {
            Text(
                title.uppercase(),
                Modifier.padding(start = 16.dp, bottom = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    value: String? = null,
    destructive: Boolean = false,
    showChevron: Boolean = true,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = if (destructive) colors.error else colors.onSurface)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (destructive) colors.error else colors.onSurface)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        if (value != null) Text(value, fontSize = 14.sp, color = colors.onSurfaceVariant)
        if (showChevron && onClick != null && !destructive) {
            Icon(painterResource(R.drawable.ic_chevron_back), null, Modifier.size(16.dp).rotate(180f), tint = colors.onSurfaceVariant)
        }
    }
    if (divider) HorizontalDivider(Modifier.padding(start = if (icon != null) 50.dp else 16.dp), color = colors.outlineVariant.copy(alpha = 0.6f))
}



@Composable
fun SettingsSwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, divider: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, enabled = enabled)
    }
    if (divider) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
fun SettingsOptionRow(title: String, selected: Boolean, divider: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 15.sp)
        if (selected) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
    if (divider) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

