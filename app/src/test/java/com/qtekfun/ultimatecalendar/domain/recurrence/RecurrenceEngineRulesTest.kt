// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecurrenceEngineRulesTest {
    private fun dates(
        start: String,
        rrule: String,
        to: String,
        from: String = start
    ): List<String> = expand(allDay(start), rrule, days(from, to)).starts()

    @Test
    fun `a series without a rule is its single occurrence`() {
        val single = expand(allDay("2026-01-01"), null, days("2026-01-01", "2026-02-01"))
        assertEquals(listOf("2026-01-01"), single.starts())
        assertTrue(single.instances().none { it.isRecurring })
        assertEquals(
            emptyList<String>(),
            expand(allDay("2026-01-01"), null, days("2026-02-01", "2026-03-01")).starts()
        )
    }

    @Test
    fun `daily with an interval`() {
        assertEquals(
            listOf("2026-01-01", "2026-01-03", "2026-01-05"),
            dates("2026-01-01", "FREQ=DAILY;INTERVAL=2", to = "2026-01-07")
        )
    }

    @Test
    fun `daily filtered by weekday, month and month day`() {
        assertEquals(
            listOf("2026-01-05", "2026-01-06", "2026-01-12", "2026-01-13"),
            dates("2026-01-05", "FREQ=DAILY;BYDAY=MO,TU", to = "2026-01-14")
        )
        assertEquals(
            listOf("2026-02-01", "2026-02-02", "2026-02-03"),
            dates("2026-01-30", "FREQ=DAILY;BYMONTH=2", to = "2026-02-04")
        )
        assertEquals(
            listOf("2026-01-15", "2026-01-31"),
            dates("2026-01-01", "FREQ=DAILY;BYMONTHDAY=15,-1", to = "2026-02-01")
        )
    }

    @Test
    fun `weekly uses the weekday of the start by default`() {
        assertEquals(
            listOf("2026-01-02", "2026-01-09", "2026-01-16"),
            dates("2026-01-02", "FREQ=WEEKLY", to = "2026-01-20")
        )
    }

    @Test
    fun `weekly with several days and an interval`() {
        assertEquals(
            listOf(
                "2026-01-05",
                "2026-01-07",
                "2026-01-09",
                "2026-01-19",
                "2026-01-21",
                "2026-01-23"
            ),
            dates("2026-01-05", "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR", to = "2026-02-01")
        )
    }

    @Test
    fun `the week start changes which weeks an interval pairs`() {
        assertEquals(
            listOf("2026-08-04", "2026-08-16", "2026-08-18", "2026-08-30"),
            dates("2026-08-04", "FREQ=WEEKLY;INTERVAL=2;WKST=SU;BYDAY=TU,SU", to = "2026-08-31")
        )
        assertEquals(
            listOf("2026-08-04", "2026-08-09", "2026-08-18", "2026-08-23"),
            dates("2026-08-04", "FREQ=WEEKLY;INTERVAL=2;WKST=MO;BYDAY=TU,SU", to = "2026-08-31")
        )
    }

    @Test
    fun `weekly never yields days before the start`() {
        assertEquals(
            listOf("2026-01-12", "2026-01-19"),
            dates("2026-01-07", "FREQ=WEEKLY;BYDAY=MO", to = "2026-01-20")
        )
    }

    @Test
    fun `weekly filtered by month and month day`() {
        assertEquals(
            listOf("2026-02-06", "2026-02-13", "2026-02-20", "2026-02-27"),
            dates("2026-01-02", "FREQ=WEEKLY;BYMONTH=2", to = "2026-03-01")
        )
        assertEquals(
            listOf("2026-02-13", "2026-03-13", "2026-11-13"),
            dates("2026-01-02", "FREQ=WEEKLY;BYDAY=FR;BYMONTHDAY=13", to = "2027-01-01")
        )
    }

    @Test
    fun `monthly keeps the day of the start and skips months without it`() {
        assertEquals(
            listOf("2026-01-31", "2026-03-31", "2026-05-31"),
            dates("2026-01-31", "FREQ=MONTHLY", to = "2026-06-01")
        )
    }

    @Test
    fun `monthly with an interval and month filters`() {
        assertEquals(
            listOf("2026-01-10", "2026-04-10", "2026-07-10"),
            dates("2026-01-10", "FREQ=MONTHLY;INTERVAL=3", to = "2026-08-01")
        )
        assertEquals(
            listOf("2026-03-15", "2026-06-15"),
            dates("2026-01-15", "FREQ=MONTHLY;BYMONTH=3,6", to = "2026-08-01")
        )
    }

    @Test
    fun `monthly by day counted from the end of the month`() {
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
            dates("2026-01-31", "FREQ=MONTHLY;BYMONTHDAY=-1", to = "2026-05-01")
        )
        assertEquals(
            listOf("2026-01-31", "2026-03-01", "2026-03-31"),
            dates("2026-01-31", "FREQ=MONTHLY;BYMONTHDAY=31,-31", to = "2026-04-01")
        )
    }

    @Test
    fun `monthly by weekday with ordinals`() {
        assertEquals(
            listOf("2026-01-16", "2026-02-20", "2026-03-20"),
            dates("2026-01-16", "FREQ=MONTHLY;BYDAY=3FR", to = "2026-04-01")
        )
        assertEquals(
            listOf("2026-01-30", "2026-02-27", "2026-03-27"),
            dates("2026-01-30", "FREQ=MONTHLY;BYDAY=-1FR", to = "2026-04-01")
        )
        assertEquals(
            listOf("2026-01-05", "2026-01-12", "2026-01-19", "2026-01-26", "2026-02-02"),
            dates("2026-01-05", "FREQ=MONTHLY;BYDAY=MO", to = "2026-02-03")
        )
    }

    @Test
    fun `a fifth weekday only exists in some months`() {
        assertEquals(
            listOf("2026-03-30"),
            dates("2026-01-05", "FREQ=MONTHLY;BYDAY=5MO", to = "2026-04-01")
        )
        assertEquals(
            emptyList<String>(),
            dates("2026-01-05", "FREQ=MONTHLY;BYDAY=-5MO", to = "2026-02-28")
        )
    }

    @Test
    fun `monthly month day and weekday together keep only their intersection`() {
        assertEquals(
            listOf("2026-02-13", "2026-03-13", "2026-11-13"),
            dates("2026-01-02", "FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13", to = "2027-01-01")
        )
    }

    @Test
    fun `BYSETPOS picks from the days of each month`() {
        assertEquals(
            listOf("2026-01-27", "2026-02-24"),
            dates("2026-01-27", "FREQ=MONTHLY;BYDAY=MO,TU;BYSETPOS=-1", to = "2026-03-01")
        )
        assertEquals(
            listOf("2026-01-30", "2026-02-27", "2026-03-31"),
            dates("2026-01-30", "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1", to = "2026-04-01")
        )
        assertEquals(
            listOf("2026-01-01", "2026-01-30", "2026-02-02", "2026-02-27"),
            dates(
                "2026-01-01",
                "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=1,-1",
                to = "2026-03-01"
            )
        )
        assertEquals(
            emptyList<String>(),
            dates("2026-01-01", "FREQ=MONTHLY;BYDAY=MO;BYSETPOS=9", to = "2026-03-01")
        )
    }

    @Test
    fun `yearly keeps the date of the start and skips February 29 in common years`() {
        assertEquals(
            listOf("2024-02-29", "2028-02-29"),
            dates("2024-02-29", "FREQ=YEARLY", to = "2029-01-01")
        )
        assertEquals(
            listOf("2026-06-01", "2028-06-01", "2030-06-01"),
            dates("2026-06-01", "FREQ=YEARLY;INTERVAL=2", to = "2031-01-01")
        )
    }

    @Test
    fun `yearly with months and month days`() {
        assertEquals(
            listOf("2026-03-01", "2026-09-01", "2027-03-01"),
            dates("2026-03-01", "FREQ=YEARLY;BYMONTH=3,9;BYMONTHDAY=1", to = "2027-04-01")
        )
        assertEquals(
            listOf("2026-01-31", "2027-01-31"),
            dates("2026-01-31", "FREQ=YEARLY;BYMONTH=1,2", to = "2027-12-31")
        )
    }

    @Test
    fun `yearly weekdays count their ordinal in the month when a month is given`() {
        assertEquals(
            listOf("2026-10-25", "2027-10-31"),
            dates("2026-10-25", "FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU", to = "2028-01-01")
        )
    }

    @Test
    fun `yearly weekdays count their ordinal in the year without a month`() {
        assertEquals(
            listOf("2026-05-18", "2027-05-17"),
            dates("2026-05-18", "FREQ=YEARLY;BYDAY=20MO", to = "2028-01-01")
        )
        assertEquals(
            listOf("2026-12-27", "2027-12-26"),
            dates("2026-12-27", "FREQ=YEARLY;BYDAY=-1SU", to = "2028-01-01")
        )
        assertEquals(
            listOf("2026-02-13", "2026-03-13", "2026-11-13"),
            dates("2026-01-02", "FREQ=YEARLY;BYDAY=FR;BYMONTHDAY=13", to = "2027-01-01")
        )
    }

    @Test
    fun `COUNT stops after that many occurrences`() {
        assertEquals(
            listOf("2026-01-01", "2026-01-02", "2026-01-03"),
            dates("2026-01-01", "FREQ=DAILY;COUNT=3", to = "2026-12-31")
        )
        assertEquals(
            emptyList<String>(),
            dates("2026-01-01", "FREQ=DAILY;COUNT=3", from = "2026-06-01", to = "2026-12-31")
        )
    }

    @Test
    fun `UNTIL as a day is inclusive`() {
        assertEquals(
            listOf("2026-01-01", "2026-01-02", "2026-01-03"),
            dates("2026-01-01", "FREQ=DAILY;UNTIL=20260103", to = "2026-12-31")
        )
    }

    @Test
    fun `the earlier of COUNT and UNTIL wins`() {
        assertEquals(
            listOf("2026-01-01", "2026-01-02"),
            dates("2026-01-01", "FREQ=DAILY;COUNT=2;UNTIL=20260110", to = "2026-12-31")
        )
        assertEquals(
            listOf("2026-01-01", "2026-01-02"),
            dates("2026-01-01", "FREQ=DAILY;COUNT=5;UNTIL=20260102", to = "2026-12-31")
        )
    }

    @Test
    fun `UNTIL as a moment on an all-day series counts as its UTC day`() {
        assertEquals(
            listOf("2026-01-01", "2026-01-02", "2026-01-03"),
            dates("2026-01-01", "FREQ=DAILY;UNTIL=20260103T230000Z", to = "2026-12-31")
        )
    }

    @Test
    fun `UNTIL as a moment on a timed series is inclusive to the second`() {
        val ten = timed("2026-01-01T10:00")
        val inclusive =
            expand(ten, "FREQ=DAILY;UNTIL=20260103T090000Z", days("2026-01-01", "2026-02-01"))
        assertEquals(
            listOf("2026-01-01T10:00", "2026-01-02T10:00", "2026-01-03T10:00"),
            inclusive.starts()
        )
        val before =
            expand(ten, "FREQ=DAILY;UNTIL=20260103T085959Z", days("2026-01-01", "2026-02-01"))
        assertEquals(listOf("2026-01-01T10:00", "2026-01-02T10:00"), before.starts())
    }

    @Test
    fun `UNTIL as a day on a timed series ends on that day in the zone of the series`() {
        val late = timed("2026-01-01T23:30")
        assertEquals(
            listOf("2026-01-01T23:30", "2026-01-02T23:30"),
            expand(late, "FREQ=DAILY;UNTIL=20260102", days("2026-01-01", "2026-02-01")).starts()
        )
    }

    @Test
    fun `rules the app cannot read are reported, not thrown`() {
        val range = days("2026-01-01", "2026-02-01")
        assertEquals(
            Expansion.Unsupported(UnsupportedReason.UNPARSEABLE_RULE),
            expand(allDay("2026-01-01"), "FREQ=DAILY;BYHOUR=3", range)
        )
        assertEquals(
            Expansion.Unsupported(UnsupportedReason.ORDINAL_WEEKDAY),
            expand(allDay("2026-01-05"), "FREQ=WEEKLY;BYDAY=2MO", range)
        )
        assertEquals(
            Expansion.Unsupported(UnsupportedReason.ORDINAL_WEEKDAY),
            expand(allDay("2026-01-05"), "FREQ=DAILY;BYDAY=-1MO", range)
        )
    }

    @Test
    fun `a rule that never matches gives up after the iteration cap`() {
        val forever = TimeRange(Instant.EPOCH, Instant.MAX)
        val result = expand(allDay("2026-01-31"), "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30", forever)
        assertEquals(Expansion.LimitReached(emptyList()), result)
    }

    @Test
    fun `a rule that never matches stops at the end of a range`() {
        val result =
            expand(
                allDay("2026-01-31"),
                "FREQ=MONTHLY;BYMONTH=2;BYMONTHDAY=30",
                days("2026-01-01", "2030-01-01")
            )
        assertEquals(Expansion.Complete(emptyList()), result)
    }
}
