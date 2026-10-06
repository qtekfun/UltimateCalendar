// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.widget

import com.qtekfun.ultimatecalendar.domain.agenda.AgendaDays
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AgendaWidgetRowsTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.parse("2026-10-06")
    private val now = Instant.parse("2026-10-06T10:00:00Z") // 12:00 in Madrid

    private fun timed(
        id: Long,
        title: String,
        start: String,
        end: String,
        status: AttendeeStatus? = null
    ) = EventInstance(
        EventId(id),
        CalendarId(1),
        title,
        EventTime.Timed(Instant.parse(start), Instant.parse(end), zone),
        selfStatus = status
    )

    private fun allDay(id: Long, title: String, from: String, to: String) = EventInstance(
        EventId(id),
        CalendarId(1),
        title,
        EventTime.AllDay(LocalDate.parse(from), LocalDate.parse(to))
    )

    private fun rows(
        instances: List<EventInstance>,
        max: Int = AgendaWidgetRows.MAX_EVENTS
    ): List<AgendaWidgetRow> {
        val days = AgendaDays.build(AgendaWidgetRows.range(today.minusDays(1)), zone, instances)
        return AgendaWidgetRows.build(days, today, now, max)
    }

    private fun titles(rows: List<AgendaWidgetRow>) = rows.map {
        when (it) {
            is AgendaWidgetRow.Day -> "#${it.date}"
            is AgendaWidgetRow.Event -> it.entry.instance.title
            is AgendaWidgetRow.More -> "+${it.count}"
        }
    }

    @Test
    fun `the range is today and a month ahead`() {
        val range = AgendaWidgetRows.range(today)

        assertEquals(today, range.start)
        assertEquals(today.plusDays(30), range.endExclusive)
    }

    @Test
    fun `days with events get a header and all-day events come first`() {
        val result = rows(
            listOf(
                timed(1, "Lunch", "2026-10-06T12:00:00Z", "2026-10-06T13:00:00Z"),
                allDay(2, "Holiday", "2026-10-06", "2026-10-07"),
                timed(3, "Dinner", "2026-10-07T18:00:00Z", "2026-10-07T19:00:00Z")
            )
        )

        assertEquals(
            listOf("#2026-10-06", "Holiday", "Lunch", "#2026-10-07", "Dinner"),
            titles(result)
        )
        assertEquals(AgendaWidgetRow.Day(today, isToday = true, isTomorrow = false), result[0])
        assertEquals(
            AgendaWidgetRow.Day(today.plusDays(1), isToday = false, isTomorrow = true),
            result[3]
        )
    }

    @Test
    fun `days before today and today's finished events are left out`() {
        val result = rows(
            listOf(
                timed(1, "Yesterday", "2026-10-05T09:00:00Z", "2026-10-05T10:00:00Z"),
                timed(2, "Breakfast", "2026-10-06T06:00:00Z", "2026-10-06T07:00:00Z"),
                timed(3, "Meeting", "2026-10-06T09:00:00Z", "2026-10-06T10:00:00Z"),
                timed(4, "Running", "2026-10-06T09:30:00Z", "2026-10-06T10:30:00Z"),
                timed(5, "Open ended", "2026-10-06T07:00:00Z", "2026-10-06T07:00:00Z")
            )
        )

        // Over at 10:00Z: Breakfast and Meeting (ends exactly now). Still going: the others.
        assertEquals(listOf("#2026-10-06", "Open ended", "Running"), titles(result))
    }

    @Test
    fun `an event from yesterday that goes on today stays, one that ended is gone`() {
        val result = rows(
            listOf(
                timed(1, "Night shift", "2026-10-05T20:00:00Z", "2026-10-06T11:00:00Z"),
                timed(2, "Late night", "2026-10-05T20:00:00Z", "2026-10-06T08:00:00Z"),
                allDay(3, "Trip", "2026-10-04", "2026-10-07")
            )
        )

        assertEquals(listOf("#2026-10-06", "Trip", "Night shift"), titles(result))
    }

    @Test
    fun `a day left without events has no header`() {
        val over = rows(listOf(timed(1, "Over", "2026-10-06T06:00:00Z", "2026-10-06T07:00:00Z")))

        assertTrue(over.isEmpty())
        assertTrue(rows(emptyList()).isEmpty())
    }

    @Test
    fun `events beyond the limit become one more row with their count`() {
        val events = (1..5).map {
            timed(it.toLong(), "E$it", "2026-10-07T0$it:00:00Z", "2026-10-07T0$it:30:00Z")
        } + timed(9, "Later", "2026-10-08T09:00:00Z", "2026-10-08T10:00:00Z")

        val result = rows(events, max = 3)

        assertEquals(listOf("#2026-10-07", "E1", "E2", "E3", "+3"), titles(result))
        assertEquals(3, (result.last() as AgendaWidgetRow.More).count)
    }

    @Test
    fun `the limit may end exactly at a day and no header is left alone`() {
        val events = listOf(
            timed(1, "A", "2026-10-07T09:00:00Z", "2026-10-07T10:00:00Z"),
            timed(2, "B", "2026-10-07T11:00:00Z", "2026-10-07T12:00:00Z"),
            timed(3, "C", "2026-10-08T09:00:00Z", "2026-10-08T10:00:00Z")
        )

        assertEquals(listOf("#2026-10-07", "A", "B", "+1"), titles(rows(events, max = 2)))
        assertEquals(
            listOf("#2026-10-07", "A", "B", "#2026-10-08", "C"),
            titles(rows(events, max = 3))
        )
    }

    @Test
    fun `a tap on an event opens that occurrence and pending invitations are known`() {
        val invite = timed(
            1,
            "Invite",
            "2026-10-07T09:00:00Z",
            "2026-10-07T10:00:00Z",
            AttendeeStatus.NEEDS_ACTION
        )
        val event = rows(listOf(invite))[1] as AgendaWidgetRow.Event

        assertTrue(event.entry.isPending)
        assertTrue(event.tap is WidgetTap.OpenEvent)
        assertEquals(event.tap, WidgetTap.decode(event.tap.encode()))
    }

    @Test
    fun `at least one event must fit`() {
        assertThrows<IllegalArgumentException> { rows(emptyList(), max = 0) }
    }
}
