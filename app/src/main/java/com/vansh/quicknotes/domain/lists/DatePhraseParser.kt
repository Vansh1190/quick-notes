package com.vansh.quicknotes.domain.lists

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Date-phrase resolution (spec §7): "tomorrow morning", "kal shaam 5 baje", "friday",
 * "in 2 hours", "on 25/12 at 6pm" → epoch millis. English + common Hindi/Hinglish words.
 * Deliberately conservative: unknown phrasing returns null rather than a wrong time.
 */
object DatePhraseParser {

    private val inHours = Regex("\\bin\\s+(\\d+)\\s*(hour|hours|ghante|ghanta|ghante\\s+mein)\\b", RegexOption.IGNORE_CASE)
    private val inDays = Regex("\\bin\\s+(\\d+)\\s*(day|days|din|din\\s+mein)\\b", RegexOption.IGNORE_CASE)
    private val atClock = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje|baj)\\b", RegexOption.IGNORE_CASE)
    private val dayNames = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    private val dayNamesHi = listOf("ravivaar", "somvaar", "somwar", "mangal", "budh", "guruvaar", "shukravaar", "shanivaar", "shanivaar")

    /** @return epoch millis or null when no phrase is present. */
    fun parse(text: String, now: Long = System.currentTimeMillis(), timeZone: TimeZone = TimeZone.getDefault()): Long? {
        val lower = text.lowercase(Locale.getDefault())
        val cal = GregorianCalendar(timeZone).apply { timeInMillis = now }

        var dayOffset: Int? = null
        var slot: Int? = null // hour-of-day hint from morning/evening words

        when {
            Regex("\\btomorrow\\b|\\bkal\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> dayOffset = 1
            Regex("\\btoday\\b|\\bao\\s*aaj\\b|\\baaj\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> dayOffset = 0
            Regex("\\bday\\s+after\\s+tomorrow\\b|\\bparso\\b|\\bparsona\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> dayOffset = 2
        }

        if (dayOffset == null) {
            val nextMatch = Regex("\\bnext\\s+week\\b", RegexOption.IGNORE_CASE).find(lower)
            if (nextMatch != null) dayOffset = 7
        }
        if (dayOffset == null) {
            dayNames.forEachIndexed { index, name ->
                if (dayOffset == null && Regex("\\b$name\\b").containsMatchIn(lower)) {
                    dayOffset = daysUntil(index, cal)
                }
            }
        }
        if (dayOffset == null) {
            dayNamesHi.forEachIndexed { hiIndex, name ->
                val target = when (hiIndex) {
                    0 -> 0; 1, 2 -> 1; 3 -> 2; 4 -> 3; 5 -> 4; 6, 7, 8 -> 6; else -> return@forEachIndexed
                }
                if (dayOffset == null && Regex("\\b$name\\b").containsMatchIn(lower)) {
                    dayOffset = daysUntil(target, cal)
                }
            }
        }

        inHours.find(lower)?.let { m ->
            val hours = m.groupValues[1].toIntOrNull() ?: return@let
            return cal.timeInMillis + hours * 3_600_000L
        }
        inDays.find(lower)?.let { m ->
            val days = m.groupValues[1].toIntOrNull() ?: return@let
            return cal.timeInMillis + days * 86_400_000L
        }

        if (dayOffset == null) return null

        when {
            Regex("\\bmorning\\b|\\bsubah\\b|\\bsubaah\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> slot = 8
            Regex("\\bafternoon\\b|\\bnoon\\b|\\bdopahar\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> slot = 13
            Regex("\\bevening\\b|\\bshaam\\b|\\bshyaam\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> slot = 18
            Regex("\\bnight\\b|\\bnoon\\b|\\bmidnight\\b|\\brat\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> slot = 21
        }

        val clock = atClock.find(lower)
        val hour: Int?
        val minute: Int
        if (clock != null) {
            var h = clock.groupValues[1].toInt()
            val marker = clock.groupValues[3].lowercase()
            minute = clock.groupValues[2].toIntOrNull() ?: 0
            val pmish = marker == "pm" || marker.startsWith("baj")
            if (pmish && h < 12) h += 12
            if (!pmish && marker == "am" && h == 12) h = 0
            hour = h.coerceIn(0, 23)
        } else {
            hour = slot
            minute = 0
        }

        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        if (hour != null) {
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
        }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun daysUntil(targetWeekday: Int, cal: Calendar): Int {
        val today = cal.get(Calendar.DAY_OF_WEEK) - 1
        var diff = (targetWeekday - today + 7) % 7
        if (diff == 0) diff = 7
        return diff
    }
}
