package com.vansh.quicknotes.ui.drawing

import android.graphics.Bitmap
import com.vansh.quicknotes.domain.DrawPoint
import com.vansh.quicknotes.domain.DrawStroke
import com.vansh.quicknotes.domain.DrawTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StrokeRendererTest {

    @Test
    fun `renders strokes to non-empty png`() {
        val strokes = listOf(
            DrawStroke(listOf(DrawPoint(5f, 5f, 1f), DrawPoint(60f, 40f, 0.7f)), 0xFF007AFFL, 6f, DrawTool.PEN),
            DrawStroke(listOf(DrawPoint(10f, 80f, 1f), DrawPoint(90f, 80f, 1f)), 0xFFFF3B30L, 10f, DrawTool.HIGHLIGHTER),
        )
        val bmp = StrokeRenderer.render(strokes, 100, 100, backgroundArgb = 0xFFFFFFFF.toInt())
        assertEquals(100, bmp.width)
        val bytes = StrokeRenderer.pngBytes(bmp)
        assertTrue(bytes.size > 100)
        // PNG magic
        assertEquals(0x89.toByte(), bytes[0])
    }

    @Test
    fun `dot stroke renders and pixel is painted`() {
        val strokes = listOf(DrawStroke(listOf(DrawPoint(50f, 50f, 1f)), 0xFF000000L, 8f, DrawTool.PEN))
        val bmp = StrokeRenderer.render(strokes, 100, 100, backgroundArgb = 0xFFFFFFFF.toInt())
        val pixel = bmp.getPixel(50, 50)
        assertEquals(android.graphics.Color.BLACK, pixel)
    }
}
