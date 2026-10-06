// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.perf

import com.qtekfun.ultimatecalendar.domain.agenda.AgendaDays
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationDetector
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.month.MonthGrid
import com.qtekfun.ultimatecalendar.domain.month.MonthLayout
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.domain.recurrence.EventSeries
import com.qtekfun.ultimatecalendar.domain.recurrence.Expansion
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceEngine
import com.qtekfun.ultimatecalendar.domain.search.SearchMatcher
import com.qtekfun.ultimatecalendar.domain.search.SearchQuery
import com.qtekfun.ultimatecalendar.domain.timegrid.OverlapLayout
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridLayout
import java.time.Clock
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * T25: micro-benchmarks of the pure code behind the views, over the reference volume (5,000
 * events) and the stress one (20,000), on generated data with a fixed seed. They print their
 * numbers (`BENCH` lines in the test output) and assert only very loose upper bounds, one or two
 * orders of magnitude over a normal machine, so they fail on a complexity regression and not on a
 * slow runner. Tagged `benchmark`: `-PskipBenchmarks` leaves them out. The worst case puts every
 * event inside the visible range; the realistic case spreads them over two years, so a month
 * holds a few hundred.
 */
@Tag("benchmark")
class DomainBenchmarksTest {
    private val month = YearMonth.of(2026, 9)
    private val grid = MonthGrid.of(month, DayOfWeek.MONDAY)
    private val week = DateRange(grid.range.start.plusDays(7), grid.range.start.plusDays(14))
    private val agendaRange = DateRange(month.atDay(1), month.atEndOfMonth().plusDays(1))

    private fun inRange(all: List<EventInstance>, range: DateRange): List<EventInstance> {
        val window = range.toTimeRange(BenchData.ZONE)
        return all.filter {
            it.time.startIn(BenchData.ZONE) < window.end &&
                it.time.endIn(BenchData.ZONE) > window.start
        }
    }

    @Test
    fun monthLayout() {
        for (count in VOLUMES) {
            val realistic = inRange(BenchData.instances(count, SPREAD_DAYS), grid.range)
            val worst = BenchData.instances(count, MONTH_DAYS)
            val realisticMs = bench("MonthLayout realistic ${realistic.size} of $count events") {
                MonthLayout.build(grid, BenchData.ZONE, realistic)
            }
            val worstMs = bench("MonthLayout worst case $count in one month") {
                MonthLayout.build(grid, BenchData.ZONE, worst)
            }
            val page = MonthLayout.build(grid, BenchData.ZONE, worst)
            assertTrue(page.weeks.sumOf { it.bars.size } >= worst.size)
            assertBelow("MonthLayout realistic", realisticMs, REALISTIC_LIMIT_MS)
            assertBelow("MonthLayout worst case", worstMs, WORST_LIMIT_MS)
        }
    }

    @Test
    fun agendaDays() {
        for (count in VOLUMES) {
            val realistic = inRange(BenchData.instances(count, SPREAD_DAYS), grid.range)
            val worst = BenchData.instances(count, MONTH_DAYS)
            val realisticMs = bench("AgendaDays realistic ${realistic.size} of $count events") {
                AgendaDays.build(agendaRange, BenchData.ZONE, realistic)
            }
            val worstMs = bench("AgendaDays worst case $count in one month") {
                AgendaDays.build(agendaRange, BenchData.ZONE, worst)
            }
            assertTrue(AgendaDays.build(agendaRange, BenchData.ZONE, worst).isNotEmpty())
            assertBelow("AgendaDays realistic", realisticMs, REALISTIC_LIMIT_MS)
            assertBelow("AgendaDays worst case", worstMs, WORST_LIMIT_MS)
        }
    }

    @Test
    fun timeGridLayoutOfAWeek() {
        for (count in VOLUMES) {
            val realistic = inRange(BenchData.instances(count, SPREAD_DAYS), week)
            val worst = BenchData.instances(count, WEEK_DAYS)
            val realisticMs = bench("TimeGridLayout week realistic ${realistic.size} of $count") {
                TimeGridLayout.build(week, BenchData.ZONE, realistic)
            }
            val worstMs = bench("TimeGridLayout week worst case $count in one week") {
                TimeGridLayout.build(week, BenchData.ZONE, worst)
            }
            val page = TimeGridLayout.build(week, BenchData.ZONE, worst)
            assertTrue(page.timed.isNotEmpty())
            assertBelow("TimeGridLayout realistic", realisticMs, REALISTIC_LIMIT_MS)
            assertBelow("TimeGridLayout worst case", worstMs, WORST_LIMIT_MS)
        }
    }

    @Test
    fun overlapLayout() {
        for (count in VOLUMES) {
            // One day with `count` events that overlap in clusters: the worst case of the packer.
            val minutes = List(count) { (it * PRIME) % MINUTES_PER_DAY }
            val bestMs = bench("OverlapLayout $count items in one day") {
                OverlapLayout.arrange(
                    minutes,
                    start = { it.toLong() },
                    end = { it + LENGTH_MINUTES.toLong() }
                )
            }
            val placed = OverlapLayout.arrange(minutes, { it.toLong() }, {
                it +
                    LENGTH_MINUTES.toLong()
            })
            assertEquals(count, placed.size)
            assertBelow("OverlapLayout", bestMs, WORST_LIMIT_MS)
        }
    }

    @Test
    fun searchMatcher() {
        val query = SearchQuery.of("review café")
        for (count in VOLUMES) {
            val events = BenchData.searchable(count)
            val bestMs = bench("SearchMatcher two words over $count events") {
                events.count { SearchMatcher.match(query, it) != null }
            }
            val found = events.count { SearchMatcher.match(query, it) != null }
            assertTrue(found in 1 until count) {
                "the query should match some events, not all: $found"
            }
            assertBelow("SearchMatcher", bestMs, WORST_LIMIT_MS)
        }
    }

    @Test
    fun recurrenceEngine() {
        // The range is two years after the first events, so a series has to be walked up to it.
        val range = TimeRange(
            BenchData.FIRST_DAY.plusDays(
                RECURRENCE_OFFSET_DAYS
            ).atStartOfDay(ZoneOffset.UTC).toInstant(),
            BenchData.FIRST_DAY.plusDays(RECURRENCE_OFFSET_DAYS + MONTH_DAYS)
                .atStartOfDay(ZoneOffset.UTC).toInstant()
        )
        for (count in VOLUMES) {
            val series = BenchData.events(count).filter { it.rrule != null }.map { EventSeries(it) }
            val bestMs = bench("RecurrenceEngine ${series.size} series of $count events") {
                series.sumOf {
                    (RecurrenceEngine.expand(it, range, BenchData.ZONE) as? Expansion.Complete)
                        ?.instances?.size ?: 0
                }
            }
            assertTrue(series.isNotEmpty())
            assertBelow("RecurrenceEngine", bestMs, WORST_LIMIT_MS)
        }
    }

    @Test
    fun invitationDetector() {
        val clock = Clock.fixed(
            BenchData.FIRST_DAY.plusDays(
                CLOCK_OFFSET_DAYS
            ).atStartOfDay(BenchData.ZONE).toInstant(),
            BenchData.ZONE
        )
        val detector = InvitationDetector(clock)
        val calendars = List(CALENDARS) {
            CalendarInfo(
                id = CalendarId(it + 1L),
                account = CalendarAccount("a@example.com", "com.example"),
                displayName = "Calendar $it",
                color = 0,
                access = CalendarAccess.OWNER,
                ownerEmail = "guest$it.0@example.com"
            )
        }
        val aliases = List(ALIASES) { "guest$it.0@example.com" }.toSet()
        for (count in VOLUMES) {
            val events = BenchData.events(count)
            val scanMs = bench("InvitationDetector.scan $count events") {
                detector.scan(events, calendars, aliases)
            }
            val scan = detector.scan(events, calendars, aliases)
            val previous = scan.pending.take(scan.pending.size / 2)
            val diffMs = bench("InvitationDetector.diff ${scan.pending.size} pending of $count") {
                detector.diff(previous, scan)
            }
            assertEquals(scan.pending.size - previous.size, detector.diff(previous, scan).new.size)
            assertBelow("InvitationDetector.scan", scanMs, WORST_LIMIT_MS)
            assertBelow("InvitationDetector.diff", diffMs, WORST_LIMIT_MS)
        }
    }

    private companion object {
        const val SPREAD_DAYS = 730
        const val MONTH_DAYS = 30
        const val WEEK_DAYS = 7
        const val MINUTES_PER_DAY = 1_440
        const val LENGTH_MINUTES = 45
        const val PRIME = 7_919
        const val RECURRENCE_OFFSET_DAYS = 700L
        const val CLOCK_OFFSET_DAYS = 300L
        const val CALENDARS = 10
        const val ALIASES = 50

        // A normal machine needs a few milliseconds for the first and under a second for the
        // second; these are about 100 times more.
        const val REALISTIC_LIMIT_MS = 1_000.0
        const val WORST_LIMIT_MS = 20_000.0
    }
}
