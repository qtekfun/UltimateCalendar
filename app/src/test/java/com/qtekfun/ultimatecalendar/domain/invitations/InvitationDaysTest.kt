// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvitationDaysTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val newYork = ZoneId.of("America/New_York")

    private fun timed(
        id: Long,
        start: String,
        hours: Long = 1,
        title: String = "T$id",
        calendar: Long = 1
    ): Invitation {
        val from = Instant.parse(start)
        return Invitation(
            InvitationKey(CalendarId(calendar), EventId(id)),
            title,
            EventTime.Timed(from, from.plusSeconds(hours * 3600), ZoneOffset.UTC)
        )
    }

    private fun allDay(id: Long, first: String, days: Long = 1, title: String = "A$id") =
        Invitation(
            InvitationKey(CalendarId(1), EventId(id)),
            title,
            EventTime.AllDay(LocalDate.parse(first), LocalDate.parse(first).plusDays(days))
        )

    @Test
    fun `no invitations make no days`() {
        assertTrue(InvitationDays.group(emptyList(), madrid).isEmpty())
    }

    @Test
    fun `invitations are sorted by start and grouped by day, across calendars`() {
        val later = timed(1, "2026-06-12T09:00:00Z", calendar = 2)
        val early = timed(2, "2026-06-10T08:00:00Z", calendar = 1)
        val sameDay = timed(3, "2026-06-10T15:00:00Z", calendar = 2)
        val days = InvitationDays.group(listOf(later, sameDay, early), madrid)
        assertEquals(
            listOf(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-12")),
            days.map { it.date }
        )
        assertEquals(listOf(early, sameDay), days[0].invitations)
        assertEquals(listOf(later), days[1].invitations)
    }

    @Test
    fun `the day is the one of the phone's zone, not the event's`() {
        // 23:30 UTC on the 10th is already the 11th in Madrid (CEST) and still the 10th in New York.
        val lateNight = timed(1, "2026-06-10T23:30:00Z")
        assertEquals(
            LocalDate.parse("2026-06-11"),
            InvitationDays.group(listOf(lateNight), madrid).single().date
        )
        assertEquals(
            LocalDate.parse("2026-06-10"),
            InvitationDays.group(listOf(lateNight), newYork).single().date
        )
    }

    @Test
    fun `the day change of daylight saving time keeps the order`() {
        // Madrid springs forward on 2026-03-29: 01:30 UTC is 03:30 local, 00:30 UTC is 01:30 local.
        val after = timed(1, "2026-03-29T01:30:00Z")
        val before = timed(2, "2026-03-29T00:30:00Z")
        val days = InvitationDays.group(listOf(after, before), madrid)
        assertEquals(1, days.size)
        assertEquals(listOf(before, after), days.single().invitations)
    }

    @Test
    fun `an all-day event leads the day and keeps its own date in any zone`() {
        val meeting = timed(1, "2026-06-10T07:00:00Z", title = "Meeting")
        val holiday = allDay(2, "2026-06-10")
        for (zone in listOf(madrid, newYork, ZoneId.of("Pacific/Kiritimati"))) {
            val day = InvitationDays.group(listOf(meeting, holiday), zone)
                .first { it.date == LocalDate.parse("2026-06-10") }
            assertEquals(holiday, day.invitations.first())
        }
    }

    @Test
    fun `at the same moment the title and then the ids decide`() {
        val b = timed(1, "2026-06-10T07:00:00Z", title = "B")
        val a = timed(2, "2026-06-10T07:00:00Z", title = "A")
        val twin1 = timed(3, "2026-06-10T07:00:00Z", title = "Same", calendar = 1)
        val twin2 = timed(4, "2026-06-10T07:00:00Z", title = "Same", calendar = 1)
        val twinOtherCalendar = timed(1, "2026-06-10T07:00:00Z", title = "Same", calendar = 2)
        val day = InvitationDays.group(
            listOf(twinOtherCalendar, twin2, b, twin1, a),
            ZoneOffset.UTC
        ).single()
        assertEquals(listOf(a, b, twin1, twin2, twinOtherCalendar), day.invitations)
    }

    @Test
    fun `a multi-day event sits on the day it starts`() {
        val trip = allDay(1, "2026-06-10", days = 3)
        val days = InvitationDays.group(listOf(trip, timed(2, "2026-06-11T10:00:00Z")), madrid)
        assertEquals(2, days.size)
        assertEquals(listOf(trip), days[0].invitations)
    }

    private val uk = Locale.UK

    @Test
    fun `a timed event shows in the phone zone with its start and end`() {
        val event = timed(1, "2026-06-10T13:00:00Z").time
        val inMadrid = InvitationTimeText.format(event, madrid, uk)
        val inNewYork = InvitationTimeText.format(event, newYork, uk)
        assertTrue(inMadrid.contains("15:00 – 16:00"), inMadrid)
        assertTrue(inNewYork.contains("09:00 – 10:00"), inNewYork)
        assertTrue(inMadrid.contains("10 Jun 2026"), inMadrid)
    }

    @Test
    fun `an event that crosses midnight names both days`() {
        val event = timed(1, "2026-06-10T21:30:00Z", hours = 3).time
        val text = InvitationTimeText.format(event, ZoneOffset.UTC, uk)
        assertTrue(text.contains("10 Jun 2026 21:30"), text)
        assertTrue(text.endsWith("11 Jun 2026 00:30"), text)
    }

    @Test
    fun `the same UTC time reads one hour later once daylight saving time starts`() {
        val before = timed(1, "2026-03-28T10:00:00Z").time
        val after = timed(2, "2026-03-29T10:00:00Z").time
        assertTrue(InvitationTimeText.format(before, madrid, uk).contains("11:00"))
        assertTrue(InvitationTimeText.format(after, madrid, uk).contains("12:00"))
    }

    @Test
    fun `all-day events show dates and do not move with the zone`() {
        val one = allDay(1, "2026-06-10").time
        val three = allDay(2, "2026-06-10", days = 3).time
        for (zone in listOf(madrid, newYork)) {
            assertEquals("10 Jun 2026", InvitationTimeText.format(one, zone, uk))
            assertEquals("10 Jun 2026 – 12 Jun 2026", InvitationTimeText.format(three, zone, uk))
        }
        assertFalse(InvitationTimeText.format(one, madrid, uk).contains(":"))
    }
}
