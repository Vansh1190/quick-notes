package com.vansh.quicknotes.ui.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SquircleShapeTest {

    private val density = object : Density {
        override val density = 1f
        override val fontScale = 1f
    }

    @Test
    fun `corner control constant is the continuous-corner value`() {
        assertEquals(0.72447f, QNSquircleShape.C, 1e-5f)
    }

    @Test
    fun `squircle path spans the full bounds and closes`() {
        val path = QNSquircleShape.squirclePath(w = 200f, h = 120f, r = 20f)
        assertTrue(path.isEmpty.not())
        val bounds = path.getBounds()
        assertEquals(0f, bounds.left, 0.6f)
        assertEquals(0f, bounds.top, 0.6f)
        assertEquals(200f, bounds.right, 0.6f)
        assertEquals(120f, bounds.bottom, 0.6f)
    }

    @Test
    fun `radius clamps to half the smaller side`() {
        val path = QNSquircleShape.squirclePath(w = 100f, h = 40f, r = 80f)
        val bounds = path.getBounds()
        assertEquals(0f, bounds.top, 0.6f)
        assertEquals(40f, bounds.bottom, 0.6f)
    }

    @Test
    fun `shape produces a generic (path) outline`() {
        val outline = QNSquircleShape(Dp(40f))
            .createOutline(Size(100f, 100f), LayoutDirection.Ltr, density)
        assertTrue(outline is Outline.Generic)
    }

    @Test
    fun `zero radius yields rectangle outline`() {
        val outline = QNSquircleShape(Dp(0f))
            .createOutline(Size(100f, 50f), LayoutDirection.Ltr, density)
        assertTrue(outline is Outline.Rectangle)
    }
}

class DesignTokensTest {

    private fun assertCloseToHex(color: androidx.compose.ui.graphics.Color, rgbHex: Int, label: String) {
        assertEquals(label + " red", ((rgbHex shr 16) and 0xFF) / 255f, color.red, 0.004f)
        assertEquals(label + " green", ((rgbHex shr 8) and 0xFF) / 255f, color.green, 0.004f)
        assertEquals(label + " blue", (rgbHex and 0xFF) / 255f, color.blue, 0.004f)
    }

    @Test
    fun `accent default is apple notes yellow`() {
        assertCloseToHex(QNColorTokens.defaultAccent, 0xFFD60A, "accent")
        assertEquals(1f, QNColorTokens.defaultAccent.alpha, 0.001f)
    }

    @Test
    fun `light and dark surfaces match spec hexes`() {
        assertCloseToHex(QNColorTokens.lightBackground, 0xF2F2F7, "lightBackground")
        assertCloseToHex(QNColorTokens.cardDark, 0x1C1C1E, "cardDark")
        assertCloseToHex(QNColorTokens.darkBackground, 0x000000, "darkBackground")
        assertCloseToHex(QNColorTokens.lightSurface, 0xFFFFFF, "lightSurface")
    }

    @Test
    fun `motion defaults follow the spec`() {
        assertTrue(QNMotion().damping in 0.7f..0.9f)
        val reduced = QNMotion(reduced = true)
        assertEquals(180, reduced.fadeMillis)
    }
}
