package com.vansh.quicknotes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareParserTest {

    @Test
    fun `single url becomes a link`() {
        val c = ShareParser.parse(textType = true, imageBytes = null, extraText = "https://cora.ai/docs ")
        assertTrue(c is SharedContent.Link)
        assertEquals("https://cora.ai/docs", (c as SharedContent.Link).url)
    }

    @Test
    fun `prose with a url inside stays text`() {
        val c = ShareParser.parse(textType = true, imageBytes = null, extraText = "look at https://cora.ai today")
        assertTrue(c is SharedContent.Text)
    }

    @Test
    fun `image bytes win over text`() {
        val c = ShareParser.parse(textType = true, imageBytes = byteArrayOf(1, 2) to "image/png", extraText = "https://x.test")
        assertTrue(c is SharedContent.Image)
        assertEquals("image/png", (c as SharedContent.Image).mimeType)
    }

    @Test
    fun `empty text yields nothing`() {
        assertEquals(null, ShareParser.parse(textType = true, imageBytes = null, extraText = "   "))
    }

    @Test
    fun `non text non image yields nothing`() {
        assertEquals(null, ShareParser.parse(textType = false, imageBytes = null, extraText = "hi"))
    }
}
