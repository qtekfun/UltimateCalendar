// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.perf

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random

/**
 * Deterministic synthetic calendars for the benchmarks (fixed seed, no clock): about ten
 * calendars, mostly timed events with a few all-day and multi-day ones, a fifth of them
 * repeating, a quarter with attendees.
 */
internal object BenchData {
    val ZONE: ZoneId = ZoneId.of("Europe/Madrid")
    val FIRST_DAY: LocalDate = LocalDate.of(2026, 9, 1)

    private const val SEED = 20_260_706L
    private const val CALENDARS = 10
    private const val MINUTES_PER_DAY = 24 * 60
    private val WORDS = listOf(
        "Standup", "Lunch", "Review", "Dentist", "Gym", "Birthday", "Planning", "Flight",
        "Café", "Workshop", "Retro", "Concert", "Reunión", "Yoga", "Demo", "Invoice"
    )
    private val PLACES = listOf("Office", "Room 4", "Café Central", "Home", "Gym", "Zoom")
    private val RULES = listOf(
        "FREQ=DAILY;COUNT=30",
        "FREQ=WEEKLY;BYDAY=MO,WE,FR",
        "FREQ=WEEKLY;INTERVAL=2",
        "FREQ=MONTHLY;BYMONTHDAY=15",
        "FREQ=MONTHLY;BYDAY=2TU",
        "FREQ=YEARLY"
    )

    /** [count] events whose first occurrence is spread over the [spreadDays] days from [FIRST_DAY]. */
    fun events(count: Int, spreadDays: Int = DEFAULT_SPREAD): List<Event> {
        val random = Random(SEED + count)
        return List(count) { index -> event(random, index, spreadDays) }
    }

    /** [count] instances inside [days] days from [FIRST_DAY]: what a source returns for a range. */
    fun instances(count: Int, days: Int): List<EventInstance> =
        events(count, days).map { instanceOf(it) }

    fun searchable(count: Int): List<SearchableEvent> = events(count).map(SearchableEvent::of)

    fun instanceOf(event: Event, selfStatus: AttendeeStatus? = null) = EventInstance(
        eventId = event.id,
        calendarId = event.calendarId,
        title = event.title,
        time = event.time,
        location = event.location,
        color = event.color,
        isRecurring = event.isRecurring,
        selfStatus = selfStatus
    )

    private fun event(random: Random, index: Int, spreadDays: Int): Event {
        val day = FIRST_DAY.plusDays(random.nextInt(spreadDays).toLong())
        val kind = random.nextInt(PERCENT)
        val time = if (kind < ALL_DAY_PERCENT) {
            EventTime.AllDay(day, day.plusDays(1L + random.nextInt(MAX_ALL_DAY_DAYS)))
        } else {
            val start = day.atStartOfDay(ZONE).toInstant()
                .plusSeconds(random.nextInt(MINUTES_PER_DAY) * SECONDS_PER_MINUTE)
            val minutes = if (kind < LONG_PERCENT) LONG_MINUTES else 15L + 15L * random.nextInt(8)
            EventTime.Timed(start, start.plusSeconds(minutes * SECONDS_PER_MINUTE), ZONE)
        }
        val title = "${WORDS[
            random.nextInt(
                WORDS.size
            )
        ]} ${WORDS[random.nextInt(WORDS.size)]} $index"
        val attendees = if (random.nextInt(PERCENT) < ATTENDEES_PERCENT) {
            List(1 + random.nextInt(MAX_ATTENDEES)) { guest(random, it) }
        } else {
            emptyList()
        }
        return Event(
            id = EventId(index + 1L),
            calendarId = CalendarId(1L + random.nextInt(CALENDARS)),
            title = title,
            time = time,
            location = PLACES[random.nextInt(PLACES.size)].takeIf { random.nextBoolean() },
            description = description(random),
            rrule = RULES[random.nextInt(RULES.size)]
                .takeIf { random.nextInt(PERCENT) < RECURRING_PERCENT },
            attendees = attendees
        )
    }

    private fun guest(random: Random, index: Int) = Attendee(
        email = "guest${random.nextInt(GUESTS)}.$index@example.com",
        name = WORDS[random.nextInt(WORDS.size)],
        status = AttendeeStatus.entries[random.nextInt(AttendeeStatus.entries.size)]
    )

    private fun description(random: Random): String = List(DESCRIPTION_WORDS) {
        WORDS[random.nextInt(WORDS.size)].lowercase()
    }.joinToString(" ")

    private const val DEFAULT_SPREAD = 730
    private const val PERCENT = 100
    private const val ALL_DAY_PERCENT = 10
    private const val MAX_ALL_DAY_DAYS = 3
    private const val LONG_PERCENT = 15
    private const val LONG_MINUTES = 26L * 60
    private const val ATTENDEES_PERCENT = 25
    private const val RECURRING_PERCENT = 20
    private const val MAX_ATTENDEES = 6
    private const val GUESTS = 500
    private const val DESCRIPTION_WORDS = 30
    private const val SECONDS_PER_MINUTE = 60L
}
