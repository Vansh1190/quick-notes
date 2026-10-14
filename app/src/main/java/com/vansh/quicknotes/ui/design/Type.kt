package com.vansh.quicknotes.ui.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vansh.quicknotes.R

/**
 * Inter, bundled (SF Pro is not licensed for Android). Scale per spec §9:
 * Large Title 34 / Title 22 / Body 17 / Caption 13. Tight tracking on large titles.
 * Sizes are base values; the system font-scale setting still applies on top.
 */
object QNType {
    val Inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

    val largeTitle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.025).em,
    )
    val title = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.02).em,
    )
    val headline = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.01).em,
    )
    val body = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.014).em,
    )
    val callout = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.01).em,
    )
    val subheadline = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.em,
    )
    val caption = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.em,
    )
    val captionTinted = caption.copy(fontWeight = FontWeight.Medium)
}

val QNTypography = Typography(
    displayLarge = QNType.largeTitle,
    headlineMedium = QNType.title,
    titleLarge = QNType.headline,
    bodyLarge = QNType.body,
    bodyMedium = QNType.callout,
    bodySmall = QNType.subheadline,
    labelMedium = QNType.caption,
)
