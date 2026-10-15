package com.vansh.quicknotes.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Status-bar inset for screen content. The app draws edge-to-edge, so every screen has to apply
 * this or its header renders underneath the status bar / notch.
 */
fun Modifier.qnStatusBarInset(): Modifier = this.statusBarsPadding()

/** Navigation / gesture-bar inset for bottom bars and list padding. */
fun Modifier.qnNavBarInset(): Modifier = this.navigationBarsPadding()

/**
 * The app's large-title header (spec §9): optional back affordance, the title, and trailing
 * actions. Every screen uses this so headers behave and look the same.
 */
@Composable
fun QNHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backTag: String = "header_back",
    titleTag: String = "header_title",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalQNPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = "Back" }
                    .qnPressable(onClick = onBack)
                    .testTag(backTag),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", style = QNType.largeTitle, color = palette.accent)
            }
            Spacer(Modifier.width(2.dp))
        }
        Text(
            title,
            style = QNType.largeTitle,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(titleTag),
        )
        actions()
    }
}

/** Pill action for a header: always labelled or described, always at least 44 dp tall. */
@Composable
fun RowScope.QNHeaderAction(
    tag: String,
    label: String,
    glyph: String,
    emphasised: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalQNPalette.current
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (emphasised) palette.accent.copy(alpha = 0.18f) else palette.surface)
            .semantics { contentDescription = label }
            .qnPressable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = QNType.captionTinted,
            color = if (emphasised) palette.accent else palette.textSecondary,
            maxLines = 1,
        )
    }
}
