package com.vansh.quicknotes.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.vansh.quicknotes.domain.DrawStroke
import com.vansh.quicknotes.domain.StrokeGeometry

/**
 * Renders vector strokes into a Bitmap via android.graphics — the preview image saved
 * alongside the stroke payload (spec §5.4) and the thumbnail used on note cards.
 * Pure math + framework canvas, no Compose, so it also runs in JVM/Robolectric tests.
 */
object StrokeRenderer {

    fun render(strokes: List<DrawStroke>, widthPx: Int, heightPx: Int, backgroundArgb: Int? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(maxOf(1, widthPx), maxOf(1, heightPx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (backgroundArgb != null) canvas.drawColor(backgroundArgb)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            paint.color = stroke.colorArgb.toInt()
            val avgPressure = stroke.points.map { it.pressure }.average().toFloat()
            paint.strokeWidth = StrokeGeometry.widthFor(stroke.baseWidthPx, avgPressure)
            if (stroke.points.size == 1) {
                val p = stroke.points.single()
                paint.style = Paint.Style.FILL
                canvas.drawCircle(p.x, p.y, paint.strokeWidth / 2f, paint)
                paint.style = Paint.Style.STROKE
                continue
            }
            val path = Path().apply {
                moveTo(stroke.points.first().x, stroke.points.first().y)
                StrokeGeometry.bezierSegments(stroke.points).forEach { seg ->
                    cubicTo(seg[0], seg[1], seg[2], seg[3], seg[4], seg[5])
                }
            }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }

    fun pngBytes(bitmap: Bitmap): ByteArray =
        java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
