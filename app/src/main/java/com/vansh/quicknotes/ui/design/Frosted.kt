package com.vansh.quicknotes.ui.design

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Frosted-glass material for bars and sheets (spec §9): RenderEffect blur on Android 12+
 * with a translucent solid fallback below.
 */
@Composable
fun Modifier.qnFrostedSurface(shape: Shape, tintAlpha: Float? = null): Modifier {
    val palette = LocalQNPalette.current
    val alpha = tintAlpha ?: if (palette.isDark) 0.82f else 0.90f
    val fill = palette.surface.copy(alpha = alpha)
    val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer { renderEffect = BlurEffect(28f, 28f, TileMode.Decal) }
    } else {
        this
    }
    return blurred.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawOutline(outline, fill)
            drawContent()
        }
    }
}
