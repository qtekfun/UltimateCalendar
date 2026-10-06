// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import com.qtekfun.ultimatecalendar.domain.accessibility.EventSpeech
import com.qtekfun.ultimatecalendar.domain.accessibility.EventSpeechFacts
import com.qtekfun.ultimatecalendar.domain.accessibility.SpokenTime
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.ui.detail.XmlWords
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The describers against the real strings, in both languages. */
class EventSpeechWordsTest {
    private fun words(language: String) = EventSpeechWords(XmlWords.of(language))

    private val facts = EventSpeechFacts(
        "Dentist",
        SpokenTime.Range("9:00", "9:30"),
        calendarName = "Home",
        status = AttendeeStatus.NEEDS_ACTION,
        otherZoneTag = "18:00 JST"
    )

    @Test
    fun `an event reads as one phrase in English`() {
        assertEquals(
            "Dentist, 9:00 to 9:30, calendar Home, invitation not answered, " +
                "In the event's time zone: 18:00 JST",
            // XmlWords does not unescape the \' of the XML, Android does.
            EventSpeech.describe(facts, words("en")).replace("\\'", "'")
        )
    }

    @Test
    fun `an event reads as one phrase in Spanish`() {
        assertEquals(
            "Dentist, de 9:00 a 9:30, calendario Home, invitación sin responder, " +
                "En la zona horaria del evento: 18:00 JST",
            EventSpeech.describe(facts, words("es"))
        )
    }

    @Test
    fun `declined and maybe are spoken in both languages`() {
        fun say(status: AttendeeStatus, language: String) = EventSpeech.describe(
            EventSpeechFacts("A", SpokenTime.AllDay, status = status),
            words(language)
        )
        assertEquals("A, All day, declined", say(AttendeeStatus.DECLINED, "en"))
        assertEquals("A, Todo el día, rechazado", say(AttendeeStatus.DECLINED, "es"))
        assertEquals("A, All day, maybe", say(AttendeeStatus.TENTATIVE, "en"))
        assertEquals("A, Todo el día, quizá", say(AttendeeStatus.TENTATIVE, "es"))
    }

    @Test
    fun `a day cell counts its events with plurals`() {
        fun day(date: String, today: Boolean, events: Int, language: String) =
            EventSpeech.describeDay(date, today, events, words(language))
        assertEquals("Tuesday 6 October, no events", day("Tuesday 6 October", false, 0, "en"))
        assertEquals("Tuesday 6 October, 1 event", day("Tuesday 6 October", false, 1, "en"))
        assertEquals("Today, Tuesday 6 October, 3 events", day("Tuesday 6 October", true, 3, "en"))
        assertEquals("martes 6 de octubre, 1 evento", day("martes 6 de octubre", false, 1, "es"))
        assertEquals(
            "Hoy, martes 6 de octubre, 3 eventos",
            day("martes 6 de octubre", true, 3, "es")
        )
        assertEquals("martes 6 de octubre, sin eventos", day("martes 6 de octubre", false, 0, "es"))
    }

    @Test
    fun `an untitled event and an until time have words`() {
        val untitled = EventSpeechFacts("", SpokenTime.Until("11:00"))
        assertEquals("(No title), Until 11:00", EventSpeech.describe(untitled, words("en")))
        assertEquals("(Sin título), Hasta 11:00", EventSpeech.describe(untitled, words("es")))
    }
}
