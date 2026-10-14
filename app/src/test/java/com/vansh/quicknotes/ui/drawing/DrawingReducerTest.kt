package com.vansh.quicknotes.ui.drawing

import com.vansh.quicknotes.domain.DrawPoint
import com.vansh.quicknotes.domain.DrawStroke
import com.vansh.quicknotes.domain.DrawTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingReducerTest {

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float) = DrawStroke(
        points = listOf(DrawPoint(x1, y1), DrawPoint(x1 + 1f, y1), DrawPoint(x2, y2)),
        colorArgb = 0xFF000000L,
        baseWidthPx = 4f,
        tool = DrawTool.PEN,
    )

    @Test
    fun `begin point end commits one stroke`() {
        var s = DrawingReducer.reduce(DrawingState(), DrawingAction.Begin)
        s = DrawingReducer.reduce(s, DrawingAction.Point(0f, 0f, 1f))
        s = DrawingReducer.reduce(s, DrawingAction.Point(20f, 10f, 0.8f))
        s = DrawingReducer.reduce(s, DrawingAction.Point(40f, 20f, 0.6f))
        s = DrawingReducer.reduce(s, DrawingAction.End)
        assertEquals(1, s.strokes.size)
        assertEquals(3, s.strokes.single().points.size)
        assertTrue(s.canUndo)
        assertFalse(s.canRedo)
    }

    @Test
    fun `duplicate near points are dropped while drawing`() {
        var s = DrawingReducer.reduce(DrawingState(), DrawingAction.Begin)
        s = DrawingReducer.reduce(s, DrawingAction.Point(0f, 0f, 1f))
        s = DrawingReducer.reduce(s, DrawingAction.Point(0.1f, 0.1f, 1f))
        s = DrawingReducer.reduce(s, DrawingAction.Point(0.2f, 0.2f, 1f))
        assertEquals(1, s.activeStroke?.points?.size)
    }

    @Test
    fun `undo redo round trip`() {
        var s = DrawingState(strokes = listOf(line(0f, 0f, 50f, 50f), line(60f, 60f, 100f, 100f)))
        s = DrawingReducer.reduce(s, DrawingAction.Undo)
        assertEquals(1, s.strokes.size)
        assertTrue(s.canRedo)
        s = DrawingReducer.reduce(s, DrawingAction.Redo)
        assertEquals(2, s.strokes.size)
        assertFalse(s.canRedo)
    }

    @Test
    fun `new stroke after undo clears redo`() {
        var s = DrawingState(strokes = listOf(line(0f, 0f, 5f, 5f), line(10f, 10f, 20f, 20f)))
        s = DrawingReducer.reduce(s, DrawingAction.Undo)
        s = DrawingReducer.reduce(s, DrawingAction.Begin)
        s = DrawingReducer.reduce(s, DrawingAction.Point(1f, 1f, 1f))
        s = DrawingReducer.reduce(s, DrawingAction.Point(30f, 30f, 1f))
        s = DrawingReducer.reduce(s, DrawingAction.End)
        assertFalse(s.canRedo)
    }

    @Test
    fun `highlighter widens and fades stroke`() {
        var s = DrawingReducer.reduce(DrawingState(baseWidthPx = 6f, tool = DrawTool.HIGHLIGHTER), DrawingAction.Begin)
        s = DrawingReducer.reduce(s, DrawingAction.Point(0f, 0f, 1f))
        s = DrawingReducer.reduce(s, DrawingAction.Point(10f, 0f, 1f))
        s = DrawingReducer.reduce(s, DrawingAction.End)
        val stroke = s.strokes.single()
        assertEquals(18f, stroke.baseWidthPx, 1e-4f)
        assertEquals(0x59L, stroke.colorArgb ushr 24)
    }

    @Test
    fun `eraser removes touched strokes only`() {
        val a = line(0f, 0f, 50f, 0f)
        val b = line(0f, 80f, 50f, 80f)
        val s = DrawingState(strokes = listOf(a, b), tool = DrawTool.ERASER)
        val out = DrawingReducer.reduce(s, DrawingAction.ErasePath(listOf(DrawPoint(25f, 2f), DrawPoint(26f, 2f))))
        assertEquals(listOf(b), out.strokes)
    }

    @Test
    fun `clear is undoable through redo stack absence but empties strokes`() {
        var s = DrawingState(strokes = listOf(line(0f, 0f, 5f, 5f)))
        s = DrawingReducer.reduce(s, DrawingAction.Clear)
        assertTrue(s.strokes.isEmpty())
    }
}
