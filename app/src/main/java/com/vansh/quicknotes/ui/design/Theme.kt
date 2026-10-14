package com.vansh.quicknotes.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun QuickNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = QNColorTokens.defaultAccent,
    motion: QNMotion = QNMotion(),
    content: @Composable () -> Unit,
) {
    val palette = rememberQNPalette(accent, darkTheme)
    CompositionLocalProvider(
        LocalQNPalette provides palette,
        LocalQNMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) qnDarkScheme(accent) else qnLightScheme(accent),
            typography = QNTypography,
            content = content,
        )
    }
}
