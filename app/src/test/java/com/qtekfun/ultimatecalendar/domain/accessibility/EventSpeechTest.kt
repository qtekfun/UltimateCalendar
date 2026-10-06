// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.accessibility

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventSpeechTest {
    private val words = object : SpeechWords {
        override fun untitled() = "(untitled)"

        override fun allDay() = "all day"

        override fun range(from: String, to: String) = "$from to $to"

        override fun until(end: String) = "until $end"

        override fun calendar(name: String) = "calendar $name"

        override fun status(status: AttendeeStatus) = "status ${status.name}"

        override fun otherZone(tag: String) = "zone $tag"

        override fun today(date: String) = "today, $date"

        override fun dayWithoutEvents(date: String) = "$date, none"

        override fun dayWithEvents(date: String, count: Int) = "$date, $count events"
    }

    private fun say(facts: EventSpeechFacts) = EventSpeech.describe(facts, words)

    private val range = SpokenTime.Range("9:00", "9:30")

    @Test
    fun `a plain event reads title, time and calendar in that order`() {
        val facts = EventSpeechFacts("Standup", range, calendarName = "Work")
        assertEquals("Standup, 9:00 to 9:30, calendar Work", say(facts))
    }

    @Test
    fun `every part comes in the documented order`() {
        val facts = EventSpeechFacts(
            "Review",
            range,
            place = "Room 4",
            calendarName = "Work",
            status = AttendeeStatus.NEEDS_ACTION,
            otherZoneTag = "18:00 JST"
        )
        assertEquals(
            "Review, 9:00 to 9:30, Room 4, calendar Work, status NEEDS_ACTION, zone 18:00 JST",
            say(facts)
        )
    }

    @Test
    fun `an unanswered, a maybe and a declined event are named, so color is never alone`() {
        listOf(AttendeeStatus.NEEDS_ACTION, AttendeeStatus.TENTATIVE, AttendeeStatus.DECLINED)
            .forEach { status ->
                val spoken = say(EventSpeechFacts("A", range, status = status))
                assertEquals("A, 9:00 to 9:30, status ${status.name}", spoken)
            }
    }

    @Test
    fun `an accepted event and one without an answer say nothing about status`() {
        val accepted = EventSpeechFacts("A", SpokenTime.AllDay, status = AttendeeStatus.ACCEPTED)
        assertEquals("A, all day", say(accepted))
        assertEquals("A, all day", say(EventSpeechFacts("A", SpokenTime.AllDay)))
    }

    @Test
    fun `the status word alone is null for plain events`() {
        assertEquals(null, EventSpeech.statusWord(null, words))
        assertEquals(null, EventSpeech.statusWord(AttendeeStatus.ACCEPTED, words))
        assertEquals("status DECLINED", EventSpeech.statusWord(AttendeeStatus.DECLINED, words))
    }

    @Test
    fun `a blank title is read as untitled and blank extras are left out`() {
        val facts =
            EventSpeechFacts("  ", SpokenTime.Start("10:00"), place = " ", calendarName = "")
        assertEquals("(untitled), 10:00", say(facts))
    }

    @Test
    fun `the end of a multi-day event is read as until`() {
        assertEquals("Trip, until 11:00", say(EventSpeechFacts("Trip", SpokenTime.Until("11:00"))))
    }

    @Test
    fun `a day says its date, today and how many events`() {
        assertEquals("Tue, none", EventSpeech.describeDay("Tue", false, 0, words))
        assertEquals("Tue, 3 events", EventSpeech.describeDay("Tue", false, 3, words))
        assertEquals("today, Tue, 1 events", EventSpeech.describeDay("Tue", true, 1, words))
        assertEquals("today, Tue, none", EventSpeech.describeDay("Tue", true, -1, words))
    }
}
