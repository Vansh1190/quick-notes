package com.vansh.quicknotes.ui.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Apple-like continuous (squircle) corners, spec §9. Pure-Kotlin smoothed-corner
 * geometry: cubic corner arcs with control offset k = 0.72447 * r (matches iOS
 * continuous-corner curvature). Fully unit-tested in SquircleShapeTest.
 */
class QNSquircleShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(min(size.width, size.height) / 2f).coerceAtLeast(0f)
        if (r <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        }
        return Outline.Generic(squirclePath(size.width, size.height, r))
    }

    companion object {
        /** Corner control constant; 0 = circular arc, 1 = square-ish, 0.72447 ≈ continuous. */
        const val C = 0.72447f

        fun squirclePath(w: Float, h: Float, r: Float, c: Float = C): Path {
            val radius = r.coerceAtMost(min(w, h) / 2f)
            return Path().apply {
                moveTo(radius, 0f)
                lineTo(w - radius, 0f)
                cubicTo(w - radius + radius * c, 0f, w, radius - radius * c, w, radius)
                lineTo(w, h - radius)
                cubicTo(w, h - radius + radius * c, w - radius + radius * c, h, w - radius, h)
                lineTo(radius, h)
                cubicTo(radius - radius * c, h, 0f, h - radius + radius * c, 0f, h - radius)
                lineTo(0f, radius)
                cubicTo(0f, radius - radius * c, radius - radius * c, 0f, radius, 0f)
                close()
            }
        }
    }
}

object QNRadius {
    val chip = 12.dp
    val field = 14.dp
    val tile = 16.dp
    val card = 20.dp
    val sheet = 28.dp
    val pill = 999.dp
}

object QNShapes {
    val chip: Shape = QNSquircleShape(QNRadius.chip)
    val field: Shape = QNSquircleShape(QNRadius.field)
    val tile: Shape = QNSquircleShape(QNRadius.tile)
    val card: Shape = QNSquircleShape(QNRadius.card)
    val sheet: Shape = QNSquircleShape(QNRadius.sheet)
    val pill: Shape = QNSquircleShape(QNRadius.pill)
}
