package com.vansh.quicknotes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeGeometryTest {

    private fun pts(vararg pairs: Pair<Float, Float>) = pairs.map { DrawPoint(it.first, it.second) }

    @Test
    fun `bezier segments count is points minus one`() {
        val segs = StrokeGeometry.bezierSegments(pts(0f to 0f, 10f to 0f, 20f to 10f))
        assertEquals(2, segs.size)
        assertEquals(6, segs.first().size)
        // segment endpoints equal the input points
        assertEquals(10f, segs[0][4], 1e-4f)
        assertEquals(20f, segs[1][4], 1e-4f)
    }

    @Test
    fun `fewer than two points yield no segments`() {
        assertTrue(StrokeGeometry.bezierSegments(emptyList()).isEmpty())
        assertTrue(StrokeGeometry.bezierSegments(listOf(DrawPoint(1f, 1f))).isEmpty())
    }

    @Test
    fun `decimate removes jitter but keeps the ends`() {
        val noisy = pts(0f to 0f, 0.2f to 0f, 0.4f to 0f, 0.6f to 0f, 50f to 0f, 50.1f to 0f, 100f to 0f)
        val out = StrokeGeometry.decimate(noisy, minDistance = 2f)
        assertEquals(pts(0f to 0f).first(), out.first())
        assertEquals(pts(100f to 0f).first(), out.last())
        assertTrue(out.size < noisy.size)
        assertTrue(out.any { it.x == 50f })
    }

    @Test
    fun `pressure maps to width between base and the min factor`() {
        assertEquals(10f, StrokeGeometry.widthFor(10f, 1f), 1e-4f)
        val minPressure = DrawPoint.MIN_PRESSURE
        assertEquals(10f * (0.45f + 0.55f * minPressure), StrokeGeometry.widthFor(10f, 0f), 1e-3f)
        assertEquals(10f * (0.45f + 0.55f * minPressure), StrokeGeometry.widthFor(10f, minPressure / 10f), 1e-3f)
    }

    @Test
    fun `segment hit detects near and far points`() {
        assertTrue(StrokeGeometry.segmentHit(0f, 0f, 10f, 0f, 5f, 1f, radius = 2f))
        assertTrue(!StrokeGeometry.segmentHit(0f, 0f, 10f, 0f, 5f, 5f, radius = 2f))
        // beyond the segment ends, t clamps to the endpoint
        assertTrue(StrokeGeometry.segmentHit(0f, 0f, 10f, 0f, -1f, 0f, radius = 2f))
    }

    @Test
    fun `erase removes whole touched strokes and keeps untouched ones`() {
        val a = DrawStroke(pts(0f to 0f, 10f to 0f), 0xFF000000, 4f, DrawTool.PEN)
        val b = DrawStroke(pts(0f to 40f, 10f to 40f), 0xFF000000, 4f, DrawTool.PEN)
        val result = StrokeGeometry.erase(listOf(a, b), erasePoints = listOf(DrawPoint(5f, 1f)), radius = 2f)
        assertEquals(listOf(b), result)
    }

    @Test
    fun `erase of a dot erases by distance`() {
        val dot = DrawStroke(listOf(DrawPoint(5f, 5f)), 0xFF000000, 4f, DrawTool.PEN)
        val other = DrawStroke(listOf(DrawPoint(50f, 50f)), 0xFF000000, 4f, DrawTool.PEN)
        val out = StrokeGeometry.erase(listOf(dot, other), listOf(DrawPoint(6f, 5f)), radius = 2f)
        assertEquals(listOf(other), out)
    }
}
