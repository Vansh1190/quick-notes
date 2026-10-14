package com.vansh.quicknotes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlMetaParserTest {

    private val sample = """
        <html><head>
        <title>Android 16 Features &amp; Live Updates</title>
        <meta property="og:image" content="/static/hero.png" />
        <meta name="description" content="What's new in Android 16." />
        <meta content='QuickNotes &amp; Co' property="og:site_name">
        </head><body>hi</body></html>
    """.trimIndent()

    @Test
    fun `extracts title site image and description`() {
        val meta = HtmlMetaParser.parse(sample, "https://developer.android.com/about/versions/16")
        assertEquals("Android 16 Features & Live Updates", meta.title)
        assertEquals("QuickNotes & Co", meta.siteName)
        assertEquals("What's new in Android 16.", meta.description)
        assertEquals("https://developer.android.com/static/hero.png", meta.thumbnailUrl)
    }

    @Test
    fun `falls back to host as site name and no meta crashes on empty html`() {
        val meta = HtmlMetaParser.parse("", "https://www.example.com/path")
        assertNull(meta.title)
        assertEquals("example.com", meta.siteName)
    }

    @Test
    fun `og title wins over title tag`() {
        val html = """
            <title>plain</title>
            <meta property="og:title" content="OG wins">
        """.trimIndent()
        assertEquals("OG wins", HtmlMetaParser.parse(html, "https://a.test").title)
    }

    @Test
    fun `title tag with newlines and entities is cleaned`() {
        val html = "<title>\n  A\n  B &quot;quoted&quot;\n</title>"
        assertEquals("A\n  B \"quoted\"", HtmlMetaParser.parse(html, "https://a.test").title)
    }
}
