package com.vansh.quicknotes.domain.lists

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatePhraseParserTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    /** Thursday 2026-10-15 09:00 IST. */
    private val now: Long = GregorianCalendar(tz).apply {
        set(2026, Calendar.OCTOBER, 15, 9, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun parts(millis: Long): Calendar = GregorianCalendar(tz).apply { timeInMillis = millis }

    @Test
    fun `tomorrow evening lands 18 00 next day`() {
        val t = DatePhraseParser.parse("call mom tomorrow evening", now, tz)!!
        val c = parts(t)
        assertEquals(16, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `kal shaam 5 baje is tomorrow 17 00`() {
        val t = DatePhraseParser.parse("invoice bhejna kal shaam 5 baje", now, tz)!!
        val c = parts(t)
        assertEquals(16, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `friday resolves to next friday`() {
        val t = DatePhraseParser.parse("fix the login bug by friday", now, tz)!!
        val c = parts(t)
        assertEquals(Calendar.FRIDAY, c.get(Calendar.DAY_OF_WEEK))
        assertTrue(t > now)
    }

    @Test
    fun `in two hours`() {
        val t = DatePhraseParser.parse("remind me in 2 hours", now, tz)!!
        assertEquals(now + 2 * 3_600_000L, t)
    }

    @Test
    fun `plain evening without a day word is not a date`() {
        assertNull(DatePhraseParser.parse("call in the evening", now, tz))
    }

    @Test
    fun `today with clock time`() {
        val t = DatePhraseParser.parse("standup today 10:30 am", now, tz)!!
        val c = parts(t)
        assertEquals(15, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, c.get(Calendar.MINUTE))
    }
}

class DumpAndSortSplitterTest {

    @Test
    fun `spec sample splits into groceries and work lists`() {
        val text = "Need to buy milk, eggs and bread. For work, send the invoice to Rahul and fix the login bug by Friday. Also call mom in the evening."
        val lists = DumpAndSortSplitter.split(text)
        val titles = lists.map { it.title }
        assertTrue("Groceries" in titles)
        assertTrue("Work" in titles)
        val groceries = lists.first { it.title == "Groceries" }
        assertTrue(groceries.items.map { it.text.lowercase() }.any { it.contains("milk") })
        assertTrue(groceries.items.map { it.text.lowercase() }.any { it.contains("eggs") })
        val work = lists.first { it.title == "Work" }
        assertTrue(work.items.map { it.text.lowercase() }.any { it.contains("invoice") })
        assertNotNull(work.items.first { it.text.lowercase().contains("bug") }.dueAt)
        val personal = lists.first { it.title == "Personal" }
        assertTrue(personal.items.map { it.text.lowercase() }.any { it.contains("mom") })
    }

    @Test
    fun `existing list titles win over keyword buckets`() {
        val lists = DumpAndSortSplitter.split(
            "send invoice to client",
            existingListTitles = listOf("Client work"),
        )
        assertEquals("Client work", lists.single().title)
        assertEquals("Client work", lists.single().items.single().existingListTitle)
    }

    @Test
    fun `empty input yields nothing`() {
        assertTrue(DumpAndSortSplitter.split("   ").isEmpty())
    }

    @Test
    fun `hinglish items still split`() {
        val lists = DumpAndSortSplitter.split("Doodh aur eggs lena hai. Office mein invoice bhejna hai.")
        assertTrue(lists.isNotEmpty())
        val allItems = lists.flatMap { it.items }.map { it.text.lowercase() }
        assertTrue(allItems.any { it.contains("doodh") })
    }
}
