package com.routerdash.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour tokens from the mockup, one set per theme. */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val ground: Color,
    val surface: Color,
    val line: Color,
    val divider: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val link: Color,
    val navBar: Color,
    val meterTrack: Color,
    val series4g: Color,
    val series5g: Color,
    val good: Color,
    val warning: Color,
    val critical: Color,
    val goodIcon: Color,
    val warningIcon: Color,
    val criticalIcon: Color,
    val goodBg: Color,
    val warningBg: Color,
    val criticalBg: Color,
    val tipBg: Color,
    val tipLine: Color,
)

val DarkColors = AppColors(
    isDark = true,
    ground = Color(0xFF0E1014),
    surface = Color(0xFF161A20),
    line = Color(0xFF232832),
    divider = Color(0xFF1F242D),
    text = Color(0xFFE8EAEE),
    textSecondary = Color(0xFFB4BAC4),
    textMuted = Color(0xFF9AA1AD),
    accent = Color(0xFF2A78D6),
    link = Color(0xFF8FB8F0),
    navBar = Color(0xFF13161C),
    meterTrack = Color(0xFF262B34),
    series4g = Color(0xFF3987E5),
    series5g = Color(0xFFD95926),
    good = Color(0xFF0CA30C),
    warning = Color(0xFFFAB219),
    critical = Color(0xFFD03B3B),
    goodIcon = Color(0xFF0CA30C),
    warningIcon = Color(0xFFFAB219),
    criticalIcon = Color(0xFFE66767),
    goodBg = Color(0xFF15241A),
    warningBg = Color(0xFF2A2414),
    criticalBg = Color(0xFF2A1717),
    tipBg = Color(0xFF141D2A),
    tipLine = Color(0xFF23344D),
)

val LightColors = AppColors(
    isDark = false,
    ground = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    line = Color(0xFFE2E5EA),
    divider = Color(0xFFECEEF1),
    text = Color(0xFF15181D),
    textSecondary = Color(0xFF4F5663),
    textMuted = Color(0xFF5F6672),
    accent = Color(0xFF2A78D6),
    link = Color(0xFF1C5CAB),
    navBar = Color(0xFFFFFFFF),
    meterTrack = Color(0xFFE6E8EC),
    series4g = Color(0xFF2A78D6),
    series5g = Color(0xFFEB6834),
    good = Color(0xFF0CA30C),
    warning = Color(0xFFE39A00),
    critical = Color(0xFFD03B3B),
    goodIcon = Color(0xFF0A7F0A),
    warningIcon = Color(0xFFA36D00),
    criticalIcon = Color(0xFFC02F2F),
    goodBg = Color(0xFFE1F3E1),
    warningBg = Color(0xFFFDF1D6),
    criticalBg = Color(0xFFFBE4E4),
    tipBg = Color(0xFFE8F1FC),
    tipLine = Color(0xFFC4D9F5),
)

private val LocalAppColors = staticCompositionLocalOf { DarkColors }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

/** Follows the phone's dark/light setting. A manual override comes later in More > Settings. */
@Composable
fun RouterDashTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = Color.White,
            background = c.ground, onBackground = c.text,
            surface = c.surface, onSurface = c.text, onSurfaceVariant = c.textSecondary,
            outline = c.line, error = c.critical,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = Color.White,
            background = c.ground, onBackground = c.text,
            surface = c.surface, onSurface = c.text, onSurfaceVariant = c.textSecondary,
            outline = c.line, error = c.critical,
        )
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
