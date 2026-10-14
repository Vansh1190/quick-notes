package com.vansh.quicknotes.domain

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Stroke geometry, pure-Kotlin so it is unit-testable (spec §5.4 vector strokes).
 * Points are stored raw (x, y, pressure, optional tilt) in the block payload JSON;
 * rendering re-derives the smoothed path at draw time so strokes stay re-editable.
 */
data class DrawPoint(val x: Float, val y: Float, val pressure: Float = 1f) {

    companion object {
        const val MIN_PRESSURE = 0.05f
    }
}

enum class DrawTool { PEN, HIGHLIGHTER, ERASER }

object StrokeGeometry {

    /**
     * Catmull-Rom → cubic Bézier control points for a smoothed polyline.
     * Returns quads of (c1x, c1y, c2x, c2y, endx, endy) per segment.
     */
    fun bezierSegments(points: List<DrawPoint>, tension: Float = 0.5f): List<FloatArray> {
        if (points.size < 2) return emptyList()
        val out = ArrayList<FloatArray>(points.size - 1)
        for (i in 0 until points.size - 1) {
            val p0 = points.getOrNull(i - 1) ?: points[i]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points.getOrNull(i + 2) ?: p2
            val c1x = p1.x + (p2.x - p0.x) / 6f * tension
            val c1y = p1.y + (p2.y - p0.y) / 6f * tension
            val c2x = p2.x - (p3.x - p1.x) / 6f * tension
            val c2y = p2.y - (p3.y - p1.y) / 6f * tension
            out += floatArrayOf(c1x, c1y, c2x, c2y, p2.x, p2.y)
        }
        return out
    }

    /** Drop points closer than [minDistance] (and never drop the last). */
    fun decimate(points: List<DrawPoint>, minDistance: Float = 1.5f): List<DrawPoint> {
        if (points.size < 3) return points
        val out = ArrayList<DrawPoint>(points.size)
        out += points.first()
        for (i in 1 until points.size - 1) {
            val prev = out.last()
            if (hypot((points[i].x - prev.x).toDouble(), (points[i].y - prev.y).toDouble()) >= minDistance) {
                out += points[i]
            }
        }
        out += points.last()
        return out
    }

    /** Pressure-to-width: base width scaled by smoothed pressure (stylus aware). */
    fun widthFor(baseWidthPx: Float, pressure: Float, minFactor: Float = 0.45f): Float {
        val p = pressure.coerceIn(DrawPoint.MIN_PRESSURE, 1f)
        return baseWidthPx * (minFactor + (1f - minFactor) * p)
    }

    /** Eraser hit test: does the erase point at [radius] touch the segment a→b? */
    fun segmentHit(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float, radius: Float): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return hypot((px - cx).toDouble(), (py - cy).toDouble()) <= radius
    }

    /** Erase: remove whole strokes touched by the erase path (vector eraser). */
    fun erase(
        strokes: List<DrawStroke>,
        erasePoints: List<DrawPoint>,
        radius: Float,
    ): List<DrawStroke> {
        if (erasePoints.isEmpty()) return strokes
        return strokes.filterNot { stroke ->
            if (stroke.points.size < 2) {
                val point = stroke.points.firstOrNull() ?: return@filterNot false
                erasePoints.any { ep -> hypot((ep.x - point.x).toDouble(), (ep.y - point.y).toDouble()) <= radius }
            } else {
                stroke.points.windowed(2).any { (a, b) ->
                    val hit = erasePoints.any { ep -> segmentHit(a.x, a.y, b.x, b.y, ep.x, ep.y, radius) }
                    hit
                }
            }
        }
    }
}

/** One drawn stroke; serialized inside the drawing block payload. */
data class DrawStroke(
    val points: List<DrawPoint>,
    val colorArgb: Long,
    val baseWidthPx: Float,
    val tool: DrawTool,
) {
    val isEmpty: Boolean get() = points.isEmpty()
}
