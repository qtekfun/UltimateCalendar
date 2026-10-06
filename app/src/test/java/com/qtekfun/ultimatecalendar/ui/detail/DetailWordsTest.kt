// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import com.qtekfun.ultimatecalendar.domain.detail.DetailTime
import com.qtekfun.ultimatecalendar.domain.detail.ReminderLine
import com.qtekfun.ultimatecalendar.domain.detail.RepeatDescriber
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailWordsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val newYork = ZoneId.of("America/New_York")
    private val english = DetailWords(XmlWords.of("en"), Locale.ENGLISH)
    private val spanish = DetailWords(XmlWords.of("es"), Locale.forLanguageTag("es"))

    private fun repeat(words: DetailWords, rule: String) =
        words.repeat(RepeatDescriber.describe(rule, madrid))

    private fun both(rule: String, expectedEnglish: String, expectedSpanish: String) {
        assertEquals(expectedEnglish, repeat(english, rule), rule)
        assertEquals(expectedSpanish, repeat(spanish, rule), rule)
    }

    @Test
    fun `weekly on some days`() = both(
        "FREQ=WEEKLY;BYDAY=MO,WE",
        "Weekly on Monday and Wednesday",
        "Semanalmente los lunes y miércoles"
    )

    @Test
    fun `three days are listed with commas and an and`() = both(
        "FREQ=WEEKLY;BYDAY=MO,WE,FR",
        "Weekly on Monday, Wednesday and Friday",
        "Semanalmente los lunes, miércoles y viernes"
    )

    @Test
    fun `an interval is plural aware`() {
        both("FREQ=DAILY", "Daily", "Diariamente")
        both("FREQ=DAILY;INTERVAL=3", "Every 3 days", "Cada 3 días")
        both(
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO",
            "Every 2 weeks on Monday",
            "Cada 2 semanas los lunes"
        )
        both("FREQ=MONTHLY;INTERVAL=6", "Every 6 months", "Cada 6 meses")
        both("FREQ=MONTHLY", "Monthly", "Mensualmente")
        both("FREQ=YEARLY;INTERVAL=2", "Every 2 years", "Cada 2 años")
        both("FREQ=YEARLY", "Yearly", "Anualmente")
    }

    @Test
    fun `every weekday`() = both(
        "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
        "Every weekday",
        "Todos los días laborables"
    )

    @Test
    fun `monthly on a position or a day of the month`() {
        both(
            "FREQ=MONTHLY;BYDAY=3FR",
            "Monthly on the third Friday",
            "Mensualmente el tercer viernes"
        )
        both(
            "FREQ=MONTHLY;BYDAY=MO;BYSETPOS=-1",
            "Monthly on the last Monday",
            "Mensualmente el último lunes"
        )
        both("FREQ=MONTHLY;BYMONTHDAY=-1", "Monthly on the last day", "Mensualmente el último día")
        both(
            "FREQ=MONTHLY;BYMONTHDAY=1,15",
            "Monthly on day 1 and 15",
            "Mensualmente el día 1 y 15"
        )
        both(
            "FREQ=MONTHLY;BYMONTHDAY=15,-1",
            "Monthly on day 15 and last",
            "Mensualmente el día 15 y último"
        )
        both(
            "FREQ=MONTHLY;BYDAY=1MO",
            "Monthly on the first Monday",
            "Mensualmente el primer lunes"
        )
        both(
            "FREQ=MONTHLY;BYDAY=2TU",
            "Monthly on the second Tuesday",
            "Mensualmente el segundo martes"
        )
        both(
            "FREQ=MONTHLY;BYDAY=4WE",
            "Monthly on the fourth Wednesday",
            "Mensualmente el cuarto miércoles"
        )
        both(
            "FREQ=MONTHLY;BYDAY=5TH",
            "Monthly on the fifth Thursday",
            "Mensualmente el quinto jueves"
        )
    }

    @Test
    fun `yearly on a date, a position or some months`() {
        both("FREQ=YEARLY;BYMONTH=3;BYMONTHDAY=3", "Yearly on March 3", "Anualmente el 3 de marzo")
        both(
            "FREQ=YEARLY;BYMONTH=3;BYDAY=3FR",
            "Yearly on the third Friday of March",
            "Anualmente el tercer viernes de marzo"
        )
        both(
            "FREQ=YEARLY;BYMONTH=6,12",
            "Yearly in June and December",
            "Anualmente en junio y diciembre"
        )
    }

    @Test
    fun `how many times`() {
        both("FREQ=DAILY;COUNT=5", "Daily, 5 times", "Diariamente, 5 veces")
        both("FREQ=DAILY;COUNT=1", "Daily, once", "Diariamente, una vez")
    }

    @Test
    fun `until a date in the language of the user`() {
        val en = repeat(english, "FREQ=DAILY;UNTIL=20261031")
        val es = repeat(spanish, "FREQ=DAILY;UNTIL=20261031")
        assertTrue(en.startsWith("Daily, until ") && en.endsWith("2026"), en)
        assertTrue(es.startsWith("Diariamente, hasta el ") && es.endsWith("2026"), es)
    }

    @Test
    fun `a rule that cannot be read is custom`() = both(
        "FREQ=DAILY;BYHOUR=9",
        "Custom repetition",
        "Repetición personalizada"
    )

    @Test
    fun `an event that does not repeat has no words`() {
        assertEquals("", english.repeat(emptyList()))
    }

    @Test
    fun `the spanish many form is used for millions`() {
        val phrases = RepeatDescriber.describe("FREQ=DAILY;INTERVAL=1000000", madrid)
        assertEquals("Cada 1000000 de días", spanish.repeat(phrases))
        assertEquals("Every 1000000 days", english.repeat(phrases))
    }

    @Test
    fun `an all day event says the day and all day`() {
        val single = DetailTime.of(
            EventTime.AllDay(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-07")),
            madrid
        )
        val en = english.whenLines(single)
        assertTrue("October" in en[0] && "2026" in en[0], en[0])
        assertEquals("All day", en[1])
        val es = spanish.whenLines(single)
        assertTrue("octubre" in es[0], es[0])
        assertEquals("Todo el día", es[1])
    }

    @Test
    fun `an all day event of several days says both ends`() {
        val lines = english.whenLines(
            DetailTime.of(
                EventTime.AllDay(LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-27")),
                madrid
            )
        )
        assertEquals(2, lines.size)
        assertTrue(
            "December 24" in lines[0] && "December 26" in lines[0] && " – " in lines[0],
            lines[0]
        )
    }

    @Test
    fun `a timed event of one day has the date and the hours`() {
        val lines = english.whenLines(
            DetailTime.of(
                EventTime.Timed(
                    Instant.parse("2026-10-08T07:00:00Z"),
                    Instant.parse("2026-10-08T08:30:00Z"),
                    madrid
                ),
                madrid
            )
        )
        assertEquals(2, lines.size)
        assertTrue("October 8, 2026" in lines[0], lines[0])
        assertTrue("9:00" in lines[1] && "10:30" in lines[1] && " – " in lines[1], lines[1])
    }

    @Test
    fun `a timed event over midnight is one line with both ends`() {
        val lines = spanish.whenLines(
            DetailTime.of(
                EventTime.Timed(
                    Instant.parse("2026-10-08T21:00:00Z"),
                    Instant.parse("2026-10-08T23:00:00Z"),
                    madrid
                ),
                madrid
            )
        )
        assertEquals(1, lines.size)
        assertTrue(" – " in lines[0] && "oct" in lines[0], lines[0])
    }

    @Test
    fun `a moment without length shows only its start`() {
        val moment = Instant.parse("2026-10-08T07:00:00Z")
        val lines = english.whenLines(
            DetailTime.of(EventTime.Timed(moment, moment, madrid), madrid)
        )
        assertEquals(2, lines.size)
        assertTrue("9:00" in lines[1] && " – " !in lines[1], lines[1])
    }

    @Test
    fun `a zone that differs from the phone's is named with its own hours`() {
        val time = DetailTime.of(
            EventTime.Timed(
                Instant.parse("2026-10-08T14:00:00Z"),
                Instant.parse("2026-10-08T15:00:00Z"),
                newYork
            ),
            madrid
        )
        listOf(
            english to Locale.ENGLISH,
            spanish to Locale.forLanguageTag("es")
        ).forEach { (words, locale) ->
            val lines = words.whenLines(time)
            assertEquals(3, lines.size)
            val name = newYork.getDisplayName(TextStyle.FULL, locale)
            assertTrue(lines[2].startsWith("$name: "), lines[2])
            assertTrue("10:00" in lines[2] && "11:00" in lines[2], lines[2])
            assertTrue("16:00" in lines[1] || "4:00" in lines[1], lines[1])
        }
    }

    @Test
    fun `reminders are said in words and plurals`() {
        fun line(minutes: Int, method: ReminderMethod = ReminderMethod.ALERT) =
            ReminderLine.of(listOf(Reminder(minutes, method)), allDay = false).single()
        assertEquals("At the start", english.reminder(line(0)))
        assertEquals("Al empezar", spanish.reminder(line(0)))
        assertEquals("1 minute before", english.reminder(line(1)))
        assertEquals("10 minutos antes", spanish.reminder(line(10)))
        assertEquals("2 hours before", english.reminder(line(120)))
        assertEquals("1 día antes", spanish.reminder(line(1440)))
        assertEquals("2 weeks before", english.reminder(line(20_160)))
        assertEquals(
            "10 minutes before, by email",
            english.reminder(line(10, ReminderMethod.EMAIL))
        )
        assertEquals(
            "10 minutos antes, por mensaje de texto",
            spanish.reminder(line(10, ReminderMethod.SMS))
        )
    }

    @Test
    fun `an all day reminder says the day before and the time`() {
        fun line(minutes: Int) = ReminderLine.of(listOf(Reminder(minutes)), allDay = true).single()
        assertTrue(english.reminder(line(900)).startsWith("1 day before, at 9:00"))
        assertTrue(english.reminder(line(2880 - 540)).startsWith("2 days before, at 9:00"))
        assertTrue(spanish.reminder(line(900)).startsWith("1 día antes, a las 9:00"))
        assertTrue(
            english.reminder(line(0)).startsWith("On the day, at 12:00") ||
                english.reminder(line(0)).startsWith("On the day, at 0:00")
        )
    }

    @Test
    fun `both languages have the same detail strings with the same placeholders`() {
        val en = XmlWords.of("en")
        val es = XmlWords.of("es")
        val names = en.stringNames.filter { it.startsWith("detail_") || it.startsWith("repeat_") }
        assertTrue(names.isNotEmpty())
        assertEquals(
            names.toSet(),
            es.stringNames.filter {
                it.startsWith("detail_") ||
                    it.startsWith("repeat_")
            }.toSet()
        )
        val placeholder = Regex("""%(\d\$)?[ds]""")
        names.forEach {
            assertEquals(
                placeholder.findAll(en.raw(it)).map { m -> m.value.takeLast(1) }.toList().sorted(),
                placeholder.findAll(es.raw(it)).map { m -> m.value.takeLast(1) }.toList().sorted(),
                it
            )
        }
        val plurals = en.pluralNames.filter { it.startsWith("detail_") || it.startsWith("repeat_") }
        plurals.forEach { name ->
            assertEquals(setOf("one", "many", "other"), en.rawPlural(name).keys, name)
            assertEquals(setOf("one", "many", "other"), es.rawPlural(name).keys, name)
        }
    }
}
