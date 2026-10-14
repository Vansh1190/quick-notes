package com.vansh.quicknotes.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Spec §9: replace Material ripples with a subtle press-scale (0.97) + dim.
 */
fun Modifier.qnPressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = LocalQNMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduced) 0.97f else 1f,
        animationSpec = motion.quick(),
        label = "qnPressScale",
    )
    this.scale(scale)
}

fun Modifier.qnPressDim(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.72f else 1f, label = "qnPressDim")
    this.graphicsLayer { this.alpha = alpha }
}

fun Modifier.qnPressable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this.qnPressScale(interaction)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Haptic tick for the spec's key moments; no-op when the system disables haptic feedback. */
@Composable
fun rememberQNHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}
