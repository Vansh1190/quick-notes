package com.vansh.quicknotes.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Apple-ish neutral surfaces (spec §9). */
object QNColorTokens {
    val lightBackground = Color(0xFFF2F2F7)
    val lightSurface = Color(0xFFFFFFFF)
    val darkBackground = Color(0xFF000000)
    val darkSurface = Color(0xFF1C1C1E)
    val groupedLight = Color(0xFFF2F2F7)
    val groupedDark = Color(0xFF000000)
    val cardLight = Color(0xFFFFFFFF)
    val cardDark = Color(0xFF1C1C1E)
    val separatorLight = Color(0x3C787880)
    val separatorDark = Color(0x4C84848A)

    val defaultAccent = Color(0xFFFFD60A)

    val textPrimaryLight = Color(0xFF000000)
    val textSecondaryLight = Color(0x99000000)
    val textTertiaryLight = Color(0x4C000000)
    val textPrimaryDark = Color(0xFFFFFFFF)
    val textSecondaryDark = Color(0x99FFFFFF)
    val textTertiaryDark = Color(0x4CFFFFFF)

    val destructive = Color(0xFFFF3B30)
    val success = Color(0xFF34C759)
}

@Immutable
data class QNPalette(
    val accent: Color,
    val background: Color,
    val surface: Color,
    val card: Color,
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val isDark: Boolean,
) {
    val onAccent: Color get() = if (accent.luminance() > 0.55) Color(0xFF000000) else Color.White
    val destructive = QNColorTokens.destructive
    val success = QNColorTokens.success
}

@Composable
fun rememberQNPalette(accent: Color = QNColorTokens.defaultAccent, dark: Boolean = isSystemInDarkTheme()): QNPalette =
    if (dark) QNPalette(
        accent = accent,
        background = QNColorTokens.darkBackground,
        surface = QNColorTokens.darkSurface,
        card = QNColorTokens.cardDark,
        separator = QNColorTokens.separatorDark,
        textPrimary = QNColorTokens.textPrimaryDark,
        textSecondary = QNColorTokens.textSecondaryDark,
        textTertiary = QNColorTokens.textTertiaryDark,
        isDark = true,
    ) else QNPalette(
        accent = accent,
        background = QNColorTokens.lightBackground,
        surface = QNColorTokens.lightSurface,
        card = QNColorTokens.cardLight,
        separator = QNColorTokens.separatorLight,
        textPrimary = QNColorTokens.textPrimaryLight,
        textSecondary = QNColorTokens.textSecondaryLight,
        textTertiary = QNColorTokens.textTertiaryLight,
        isDark = false,
    )

val LocalQNPalette = staticCompositionLocalOf<QNPalette> {
    error("QNPalette not provided; wrap content in QuickNotesTheme")
}

fun qnLightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    background = QNColorTokens.lightBackground,
    surface = QNColorTokens.lightSurface,
    error = QNColorTokens.destructive,
)

fun qnDarkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    background = QNColorTokens.darkBackground,
    surface = QNColorTokens.darkSurface,
    error = QNColorTokens.destructive,
)
