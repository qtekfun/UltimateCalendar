// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope.ALL
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope.THIS
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope.THIS_AND_FOLLOWING
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecurrenceSplitterTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun timed(local: String, minutes: Long = 60, zone: ZoneId = madrid): EventTime.Timed {
        val start = LocalDateTime.parse(local).atZone(zone).toInstant()
        return EventTime.Timed(start, start.plusSeconds(minutes * 60), zone)
    }

    private fun allDay(start: String, end: String) =
        EventTime.AllDay(LocalDate.parse(start), LocalDate.parse(end))

    private fun event(time: EventTime, rrule: String?) =
        Event(EventId(7), CalendarId(1), "Gym", time, rrule = rrule)

    // Fridays at 10:00 in Madrid from 2 October 2026; the clocks go back on 25 October.
    private val weeklyStart = timed("2026-10-02T10:00", 90)
    private val weekly = event(weeklyStart, "FREQ=WEEKLY;BYDAY=FR")
    private val afterDst = timed("2026-10-30T10:00", 90)

    private fun CalendarResult<SeriesChange>.value(): SeriesChange =
        (this as CalendarResult.Success).value

    private fun CalendarResult<SeriesChange>.split(): SeriesChange.Split =
        value() as SeriesChange.Split

    private fun CalendarResult<SeriesChange>.update(): Event =
        (value() as SeriesChange.Update).event

    private fun assertInvalid(result: CalendarResult<*>) {
        val failure = result as CalendarResult.Failure
        assertTrue(failure.error is CalendarError.Invalid)
    }

    @Test
    fun `only the events that repeat can be split`() {
        val single = event(weeklyStart, null)
        listOf(THIS, THIS_AND_FOLLOWING, ALL).forEach {
            assertInvalid(RecurrenceSplitter.edit(single, weeklyStart, single, it))
            assertInvalid(RecurrenceSplitter.delete(single, weeklyStart, it))
        }
    }

    @Test
    fun `editing only this one stores an exception that does not repeat`() {
        val edited = weekly.copy(title = "Gym, late", rrule = "FREQ=DAILY")
        assertEquals(
            SeriesChange.ReplaceOccurrence(
                afterDst,
                edited.copy(id = RecurrenceSplitter.UNSAVED, rrule = null)
            ),
            RecurrenceSplitter.edit(weekly, afterDst, edited, THIS).value()
        )
    }

    @Test
    fun `deleting only this one cancels it and leaves the series`() {
        assertEquals(
            SeriesChange.CancelOccurrence(afterDst),
            RecurrenceSplitter.delete(weekly, afterDst, THIS).value()
        )
    }

    @Test
    fun `deleting all or the first and following deletes the series`() {
        val expected = SeriesChange.Delete(weekly.id)
        assertEquals(expected, RecurrenceSplitter.delete(weekly, afterDst, ALL).value())
        assertEquals(
            expected,
            RecurrenceSplitter.delete(weekly, weeklyStart, THIS_AND_FOLLOWING).value()
        )
    }

    @Test
    fun `deleting this and following ends the series one second before, in UTC`() {
        // 10:00 in Madrid on 30 October is 09:00Z (CET); the 23rd was 08:00Z (CEST).
        assertEquals(
            SeriesChange.Split(
                weekly.copy(rrule = "FREQ=WEEKLY;BYDAY=FR;UNTIL=20261030T085959Z"),
                null
            ),
            RecurrenceSplitter.delete(weekly, afterDst, THIS_AND_FOLLOWING).value()
        )
    }

    @Test
    fun `ending a series across the daylight saving change keeps the previous occurrence`() {
        val sunday = event(timed("2026-10-18T09:30"), "FREQ=WEEKLY;BYDAY=SU")
        val occurrence = timed("2026-11-01T09:30")
        val truncated = RecurrenceSplitter.delete(
            sunday,
            occurrence,
            THIS_AND_FOLLOWING
        ).split().truncated
        val until = RecurrenceRules.parse(truncated.rrule!!)!!.until as Until.Moment
        // 1 November 09:30 CET is 08:30Z; 25 October 09:30 CET was 08:30Z too: still allowed.
        assertEquals(Instant.parse("2026-11-01T08:29:59Z"), until.at)
        assertTrue(Instant.parse("2026-10-25T08:30:00Z") <= until.at)
    }

    @Test
    fun `all-day series end on the day before`() {
        val master = event(allDay("2026-10-01", "2026-10-02"), "FREQ=DAILY;COUNT=20")
        assertEquals(
            SeriesChange.Split(master.copy(rrule = "FREQ=DAILY;UNTIL=20261004"), null),
            RecurrenceSplitter.delete(
                master,
                allDay("2026-10-05", "2026-10-06"),
                THIS_AND_FOLLOWING
            )
                .value()
        )
    }

    @Test
    fun `an occurrence before the series cannot be split off`() {
        val before = timed("2026-09-25T10:00")
        assertInvalid(RecurrenceSplitter.delete(weekly, before, THIS_AND_FOLLOWING))
        assertInvalid(RecurrenceSplitter.edit(weekly, before, weekly, THIS_AND_FOLLOWING))
    }

    @Test
    fun `a rule the app does not understand is not cut`() {
        val odd = weekly.copy(rrule = "FREQ=WEEKLY;BYHOUR=9")
        assertInvalid(RecurrenceSplitter.delete(odd, afterDst, THIS_AND_FOLLOWING))
        assertInvalid(RecurrenceSplitter.edit(odd, afterDst, odd, THIS_AND_FOLLOWING))
    }

    @Test
    fun `editing this and following starts a new series at the edited time`() {
        val edited = weekly.copy(
            title = "Gym, moved",
            time = timed("2026-10-30T18:00", 45),
            rrule = "FREQ=WEEKLY;BYDAY=FR;UNTIL=20270630T215959Z"
        )
        val split = RecurrenceSplitter.edit(weekly, afterDst, edited, THIS_AND_FOLLOWING).split()
        assertEquals(
            weekly.copy(rrule = "FREQ=WEEKLY;BYDAY=FR;UNTIL=20261030T085959Z"),
            split.truncated
        )
        assertEquals(edited.copy(id = RecurrenceSplitter.UNSAVED), split.newSeries)
    }

    @Test
    fun `the new series can stop repeating`() {
        val edited = weekly.copy(time = afterDst, rrule = null)
        val split = RecurrenceSplitter.edit(weekly, afterDst, edited, THIS_AND_FOLLOWING).split()
        assertNull(split.newSeries!!.rrule)
    }

    @Test
    fun `an unchanged rule without a count is copied as it is`() {
        val edited = weekly.copy(time = afterDst)
        val split = RecurrenceSplitter.edit(weekly, afterDst, edited, THIS_AND_FOLLOWING, 4).split()
        assertEquals("FREQ=WEEKLY;BYDAY=FR", split.newSeries!!.rrule)
    }

    @Test
    fun `a counted series hands the remaining occurrences to the new series`() {
        val master = weekly.copy(rrule = "FREQ=WEEKLY;BYDAY=FR;COUNT=10")
        val edited = master.copy(time = afterDst)
        // 2, 9, 16 and 23 October come before 30 October.
        val split = RecurrenceSplitter.edit(master, afterDst, edited, THIS_AND_FOLLOWING, 4).split()
        assertEquals("FREQ=WEEKLY;BYDAY=FR;UNTIL=20261030T085959Z", split.truncated.rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=FR;COUNT=6", split.newSeries!!.rrule)
        val last = RecurrenceSplitter.edit(master, afterDst, edited, THIS_AND_FOLLOWING, 9).split()
        assertEquals("FREQ=WEEKLY;BYDAY=FR;COUNT=1", last.newSeries!!.rrule)
    }

    @Test
    fun `a count that does not fit the occurrence is refused`() {
        val master = weekly.copy(rrule = "FREQ=WEEKLY;BYDAY=FR;COUNT=10")
        val edited = master.copy(time = afterDst)
        listOf(0, 10, 11).forEach {
            assertInvalid(RecurrenceSplitter.edit(master, afterDst, edited, THIS_AND_FOLLOWING, it))
        }
    }

    @Test
    fun `a rule the user changed wins over the old count`() {
        val master = weekly.copy(rrule = "FREQ=WEEKLY;BYDAY=FR;COUNT=10")
        val edited = master.copy(time = afterDst, rrule = "FREQ=DAILY;COUNT=3")
        val split = RecurrenceSplitter.edit(master, afterDst, edited, THIS_AND_FOLLOWING, 0).split()
        assertEquals("FREQ=DAILY;COUNT=3", split.newSeries!!.rrule)
    }

    @Test
    fun `editing all moves the series by the same days at the new time and length`() {
        // The 30th (CET) is dragged to Saturday 31st at 11:00 for 30 minutes.
        val edited = weekly.copy(
            title = "Gym, Saturdays",
            time = timed("2026-10-31T11:00", 30),
            rrule = "FREQ=WEEKLY;BYDAY=SA"
        )
        val updated = RecurrenceSplitter.edit(weekly, afterDst, edited, ALL).update()
        // 3 October is still summer time: 11:00 local is 09:00Z.
        assertEquals(edited.copy(time = timed("2026-10-03T11:00", 30)), updated)
        assertEquals(Instant.parse("2026-10-03T09:00:00Z"), (updated.time as EventTime.Timed).start)
    }

    @Test
    fun `editing this and following on the first occurrence edits the whole series`() {
        val edited = weekly.copy(time = timed("2026-10-02T10:15", 90))
        assertEquals(
            SeriesChange.Update(edited),
            RecurrenceSplitter.edit(weekly, weeklyStart, edited, THIS_AND_FOLLOWING).value()
        )
    }

    @Test
    fun `editing all keeps the wall clock in the new zone`() {
        val london = ZoneId.of("Europe/London")
        val edited = weekly.copy(time = timed("2026-10-30T10:00", 90, london))
        assertEquals(
            timed("2026-10-02T10:00", 90, london),
            RecurrenceSplitter.edit(weekly, afterDst, edited, ALL).update().time
        )
    }

    @Test
    fun `all-day series move by days and keep the edited length`() {
        val master = event(allDay("2026-10-01", "2026-10-02"), "FREQ=WEEKLY")
        val edited = master.copy(time = allDay("2026-10-09", "2026-10-12"))
        val updated =
            RecurrenceSplitter.edit(
                master,
                allDay("2026-10-08", "2026-10-09"),
                edited,
                ALL
            ).update()
        assertEquals(allDay("2026-10-02", "2026-10-05"), updated.time)
    }

    @Test
    fun `a timed series made all-day and an all-day one made timed`() {
        val toAllDay = weekly.copy(time = allDay("2026-10-31", "2026-11-01"))
        assertEquals(
            allDay("2026-10-03", "2026-10-04"),
            RecurrenceSplitter.edit(weekly, afterDst, toAllDay, ALL).update().time
        )

        val master = event(allDay("2026-10-01", "2026-10-02"), "FREQ=WEEKLY")
        val toTimed = master.copy(time = timed("2026-10-15T08:00"))
        assertEquals(
            timed("2026-10-08T08:00"),
            RecurrenceSplitter.edit(master, allDay("2026-10-08", "2026-10-09"), toTimed, ALL)
                .update().time
        )
    }

    @Test
    fun `a time that does not exist on the series day falls forward`() {
        // 29 March 2026 02:30 does not exist in Madrid: the clocks jump from 02:00 to 03:00.
        val master = event(timed("2026-03-28T02:30"), "FREQ=DAILY")
        val edited = master.copy(time = timed("2026-04-05T02:30"))
        val updated = RecurrenceSplitter.edit(
            master,
            timed("2026-04-04T02:30"),
            edited,
            ALL
        ).update()
        // Moved one day: 29 March 02:30 does not exist, so it becomes 03:30 CEST = 01:30Z.
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), (updated.time as EventTime.Timed).start)
    }
}
