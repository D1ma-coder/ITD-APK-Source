package com.itd.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val ItdBlue = Color(0xFF2979FF)

private val DarkColors = darkColorScheme(
    primary = ItdBlue,
    onPrimary = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFE2E2E2),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFE2E2E2),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF888888),
    surfaceTint = ItdBlue,
    error = Color(0xFFFF453A),
    outline = Color(0xFF3A3A40),
    outlineVariant = Color(0xBE2A2A2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
)

private val LightColors = lightColorScheme(
    primary = ItdBlue,
    onPrimary = Color.White,
    background = Color(0xFFF0F2F5),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF626262),
    error = Color(0xFFFF3B30),
    outline = Color(0xFFD0D0D0),
    outlineVariant = Color(0xFFDCDCDC),
    surfaceContainerHighest = Color(0xFFDADADA),
)

private val ItdTypography = Typography(
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
)

@Composable
fun ItdTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = ItdTypography, content = content)
}
