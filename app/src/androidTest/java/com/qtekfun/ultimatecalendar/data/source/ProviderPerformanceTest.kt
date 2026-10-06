// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatecalendar.data.source.provider.ContentResolverGateway
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.month.MonthGrid
import com.qtekfun.ultimatecalendar.domain.month.MonthLayout
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridLayout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * T25: how long [ProviderCalendarSource] takes to read a month, a week and a search, and the
 * year-wide read of the periodic invitation check, with 5,000 and then 20,000 events (about a
 * fifth repeating, a quarter with attendees) in ten calendars of a LOCAL test account. The
 * numbers go to logcat (tag `T25`) and are asserted only against very loose bounds.
 *
 * It seeds thousands of events, so it runs only when asked: pass the instrumentation argument
 * `perf=true` (the `UI tests` workflow does with its `perf` input). It never reads or writes any
 * calendar besides its own and deletes them afterwards. Never run it on a personal phone.
 */
class ProviderPerformanceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val random = Random(SEED)
    private var seeded = 0
    private lateinit var calendarIds: List<Long>

    @Before
    fun setUp() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("performance runs only with perf=true", arguments.getString("perf") == "true")
        grantCalendarPermissions()
        deleteTestCalendars()
        calendarIds = List(CALENDARS) { createCalendar("Perf $it") }
    }

    @After
    fun tearDown() {
        if (::calendarIds.isInitialized) deleteTestCalendars()
    }

    @Test
    fun readsAndSearchesWithReferenceAndStressVolumes() = runBlocking {
        val source = ProviderCalendarSource(ContentResolverGateway(context), Dispatchers.IO)
        val ids = calendarIds.map(::CalendarId).toSet()
        for (volume in intArrayOf(REFERENCE_EVENTS, STRESS_EVENTS)) {
            val seedMs = timed { seedUpTo(volume) }
            report("seed", volume, "ms total", seedMs)
            measure(source, ids, volume)
        }
    }

    private suspend fun measure(source: ProviderCalendarSource, ids: Set<CalendarId>, volume: Int) {
        val today = LocalDate.now(zone)
        val grid = MonthGrid.of(YearMonth.from(today), DayOfWeek.MONDAY)
        val weekStart = today.with(DayOfWeek.MONDAY)
        val month = grid.range.toTimeRange(zone)
        val week = TimeRange(
            weekStart.atStartOfDay(zone).toInstant(),
            weekStart.plusDays(DAYS_IN_WEEK).atStartOfDay(zone).toInstant()
        )
        val weekRange = DateRange(weekStart, weekStart.plusDays(DAYS_IN_WEEK))
        val year = TimeRange(Instant.now(), Instant.now().plus(DAYS_IN_YEAR, ChronoUnit.DAYS))

        var monthInstances = emptyList<EventInstance>()
        val monthFirst = timed { monthInstances = source.instances(month, ids).value() }
        report("month read, first", volume, "ms (${monthInstances.size} instances)", monthFirst)
        val monthBest = best { source.instances(month, ids).value() }
        report("month read, best of $RUNS", volume, "ms", monthBest)
        report(
            "month layout (ART)",
            volume,
            "ms",
            best {
                MonthLayout.build(grid, zone, monthInstances)
            }
        )

        var weekInstances = emptyList<EventInstance>()
        report(
            "week read, best of $RUNS",
            volume,
            "ms",
            best {
                weekInstances =
                    source.instances(week, ids).value()
            }
        )
        report(
            "week layout (ART)",
            volume,
            "ms (${weekInstances.size} instances)",
            best { TimeGridLayout.build(weekRange, zone, weekInstances) }
        )

        var yearInstances = emptyList<EventInstance>()
        val yearMs = best(runs = YEAR_RUNS) { yearInstances = source.instances(year, ids).value() }
        report(
            "year read (invitation check)",
            volume,
            "ms (${yearInstances.size} instances)",
            yearMs
        )
        val sample = yearInstances.map { it.eventId }.distinct().take(EVENT_SAMPLE)
        val perEvent = timed { sample.forEach { source.event(it) } } / sample.size.coerceAtLeast(1)
        report("event(id) read, mean of ${sample.size}", volume, "ms each", perEvent)

        measureSearches(source, ids, volume)
        assertTrue("month read too slow: $monthBest ms", monthBest < READ_LIMIT_MS)
        assertTrue("year read too slow: $yearMs ms", yearMs < YEAR_LIMIT_MS)
    }

    private suspend fun measureSearches(
        source: ProviderCalendarSource,
        ids: Set<CalendarId>,
        volume: Int
    ) {
        for ((label, query) in QUERIES) {
            var found = 0
            val ms =
                best(runs = SEARCH_RUNS) { found = source.search(query, ids, null).value().size }
            report("search $label '$query', best of $SEARCH_RUNS", volume, "ms ($found events)", ms)
            assertTrue("search '$query' too slow: $ms ms", ms < SEARCH_LIMIT_MS)
        }
    }

    private fun <T> CalendarResult<T>.value(): T = (this as CalendarResult.Success).value

    private inline fun timed(block: () -> Unit): Double {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / NANOS_PER_MS
    }

    private inline fun best(runs: Int = RUNS, block: () -> Unit): Double =
        (0 until runs).minOf { timed(block) }

    private fun report(what: String, volume: Int, unit: String, value: Double) {
        val line = "%-46s events=%6d  %10.1f %s".format(what, volume, value, unit)
        Log.i(TAG, line)
        println("$TAG $line")
    }

    /** Adds events until the test calendars hold [volume] of them, in batches of one transaction. */
    private fun seedUpTo(volume: Int) {
        val today = LocalDate.now(zone)
        while (seeded < volume) {
            val batch = minOf(BATCH_EVENTS, volume - seeded)
            val ops = ArrayList<ContentProviderOperation>()
            repeat(batch) { ops += eventOps(today, seeded + it, ops.size) }
            context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
            seeded += batch
        }
    }

    private fun eventOps(
        today: LocalDate,
        index: Int,
        firstOp: Int
    ): List<ContentProviderOperation> {
        val day = today.plusDays((random.nextInt(2 * SPREAD_DAYS) - SPREAD_DAYS).toLong())
        val kind = random.nextInt(PERCENT)
        val title = "${WORDS[
            random.nextInt(
                WORDS.size
            )
        ]} ${WORDS[random.nextInt(WORDS.size)]} $index"
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarIds[random.nextInt(CALENDARS)])
            put(Events.TITLE, title)
            put(Events.EVENT_LOCATION, "Room ${random.nextInt(ROOMS)}")
            put(
                Events.DESCRIPTION,
                List(DESCRIPTION_WORDS) {
                    WORDS[random.nextInt(WORDS.size)]
                }.joinToString(" ")
            )
        }
        if (kind < ALL_DAY_PERCENT) {
            val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            values.put(Events.ALL_DAY, 1)
            values.put(Events.EVENT_TIMEZONE, "UTC")
            values.put(Events.DTSTART, start)
            values.put(Events.DTEND, start + MILLIS_PER_DAY)
        } else {
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli() +
                random.nextInt(MINUTES_PER_DAY) * MILLIS_PER_MINUTE
            values.put(Events.EVENT_TIMEZONE, zone.id)
            values.put(Events.DTSTART, start)
            if (random.nextInt(PERCENT) < RECURRING_PERCENT) {
                values.put(Events.DURATION, "PT1H")
                values.put(Events.RRULE, RULES[random.nextInt(RULES.size)])
            } else {
                values.put(
                    Events.DTEND,
                    start + (MIN_LENGTH + random.nextInt(LENGTHS)) * MILLIS_PER_MINUTE
                )
            }
        }
        val ops = mutableListOf(
            ContentProviderOperation.newInsert(
                asSyncAdapter(Events.CONTENT_URI)
            ).withValues(values).build()
        )
        if (random.nextInt(PERCENT) < ATTENDEES_PERCENT) ops += attendeeOps(firstOp)
        return ops
    }

    private fun attendeeOps(firstOp: Int): List<ContentProviderOperation> = List(2) { guest ->
        ContentProviderOperation.newInsert(asSyncAdapter(Attendees.CONTENT_URI))
            .withValueBackReference(Attendees.EVENT_ID, firstOp)
            .withValue(Attendees.ATTENDEE_NAME, WORDS[random.nextInt(WORDS.size)])
            .withValue(
                Attendees.ATTENDEE_EMAIL,
                "guest${random.nextInt(GUESTS)}.$guest@example.com"
            )
            .withValue(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_NONE)
            .build()
    }

    private fun createCalendar(name: String): Long {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, name)
            put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, OWNER)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, "Europe/Madrid")
        }
        val uri = requireNotNull(
            context.contentResolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)
        )
        return ContentUris.parseId(uri)
    }

    private fun deleteTestCalendars() {
        context.contentResolver.delete(
            asSyncAdapter(Calendars.CONTENT_URI),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL)
        )
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun grantCalendarPermissions() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).forEach {
            ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("pm grant ${context.packageName} $it")
            ).use { stream -> stream.readBytes() }
        }
    }

    private companion object {
        const val TAG = "T25"
        const val ACCOUNT = "uc-perf-tests@example.invalid"
        const val OWNER = "me@example.com"
        const val COLOR = 0xFF0B63CE.toInt()
        const val SEED = 20_260_706L
        const val CALENDARS = 10
        const val REFERENCE_EVENTS = 5_000
        const val STRESS_EVENTS = 20_000
        const val BATCH_EVENTS = 250
        const val SPREAD_DAYS = 365
        const val PERCENT = 100
        const val ALL_DAY_PERCENT = 10
        const val RECURRING_PERCENT = 20
        const val ATTENDEES_PERCENT = 25
        const val MINUTES_PER_DAY = 1_440
        const val MIN_LENGTH = 15L
        const val LENGTHS = 8
        const val ROOMS = 30
        const val GUESTS = 500
        const val DESCRIPTION_WORDS = 30
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_DAY = 86_400_000L
        const val DAYS_IN_WEEK = 7L
        const val DAYS_IN_YEAR = 365L
        const val RUNS = 5
        const val YEAR_RUNS = 3
        const val SEARCH_RUNS = 3
        const val EVENT_SAMPLE = 100
        const val NANOS_PER_MS = 1_000_000.0

        // Very loose: they catch something going wrong, not a slow emulator.
        const val READ_LIMIT_MS = 10_000.0
        const val YEAR_LIMIT_MS = 60_000.0
        const val SEARCH_LIMIT_MS = 30_000.0

        val WORDS = listOf(
            "Standup", "Lunch", "Review", "Dentist", "Gym", "Birthday", "Planning", "Flight",
            "Cafe", "Workshop", "Retro", "Concert", "Reunion", "Yoga", "Demo", "Invoice"
        )
        val RULES = listOf(
            "FREQ=DAILY;COUNT=30",
            "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            "FREQ=WEEKLY;INTERVAL=2",
            "FREQ=MONTHLY;BYMONTHDAY=15",
            "FREQ=YEARLY"
        )
        val QUERIES = listOf(
            "common" to "review",
            "two words" to "review cafe",
            "rare" to "guest499"
        )
    }
}
