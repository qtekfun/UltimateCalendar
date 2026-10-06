// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** A source to check, with a calendar the user owns and one it can only read. */
class SourceUnderTest(
    val source: CalendarSource,
    val writable: CalendarInfo,
    val readOnly: CalendarInfo
) {
    /** "Me": the owner of [writable], used as an attendee. */
    val me: String get() = requireNotNull(writable.ownerEmail)
}

class Scenario(val name: String, val run: suspend SourceUnderTest.() -> Unit) {
    override fun toString() = name
}

/**
 * How every [CalendarSource] behaves. The same scenarios run against the fake in unit tests and,
 * in T05, against the real provider on the emulator. They use plain checks that throw
 * [AssertionError], so any test framework can run them. A failure here means the fake or the
 * provider source is wrong, not the scenario, unless the scenario contradicts the provider.
 *
 * Conventions: all-day instances are dated in UTC; an instance of a series, edited or not, has
 * `isRecurring = true`; attendees may come back in any order and with the organizer added.
 */
object CalendarSourceContract {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val noon = Instant.parse("2026-10-06T10:00:00Z")
    private val day = TimeRange(
        Instant.parse("2026-10-06T00:00:00Z"),
        Instant.parse("2026-10-07T00:00:00Z")
    )
    private val month = TimeRange(
        Instant.parse("2026-10-01T00:00:00Z"),
        Instant.parse("2026-11-01T00:00:00Z")
    )

    private fun SourceUnderTest.draft(
        title: String = "Lunch",
        start: Instant = noon,
        rrule: String? = null,
        attendees: List<Attendee> = emptyList()
    ) = EventDraft(
        calendarId = writable.id,
        title = title,
        time = EventTime.Timed(start, start.plusSeconds(HOUR), madrid),
        rrule = rrule,
        attendees = attendees
    )

    val scenarios: List<Scenario> = listOf(
        Scenario("lists the calendars with their access") {
            val found = source.calendars().value().associateBy { it.id }
            expectEquals(writable.access, found.getValue(writable.id).access, "writable access")
            expectEquals(readOnly.access, found.getValue(readOnly.id).access, "read-only access")
            expectEquals(writable.account, found.getValue(writable.id).account, "account")
        },
        Scenario("an event keeps what it was created with") {
            val created = draft(attendees = listOf(Attendee.of("ana@example.com"))).copy(
                location = "Cafe",
                description = "Notes",
                reminders = listOf(Reminder(10), Reminder(60))
            )
            val event = source.event(source.create(created).value()).value()
            expectEquals("Lunch", event.title, "title")
            expectEquals(created.time, event.time, "time")
            expectEquals("Cafe", event.location, "location")
            expectEquals("Notes", event.description, "description")
            expectEquals(
                setOf(10, 60),
                event.reminders.map {
                    it.minutesBefore
                }.toSet(),
                "reminders"
            )
            expect(event.attendees.any { it.email == "ana@example.com" }, "attendee kept")
            expectEquals(writable.id, event.calendarId, "calendar")
        },
        Scenario("an all-day event keeps its dates") {
            val dates = EventTime.AllDay(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 8))
            source.create(draft().copy(time = dates)).value()
            val instance = source.instances(month).value().single()
            expectEquals(dates, instance.time, "all-day dates")
        },
        Scenario("instances are limited to the range and sorted") {
            source.create(draft("Late", noon.plusSeconds(2 * HOUR))).value()
            source.create(draft("Early", noon)).value()
            source.create(draft("Other day", noon.plusSeconds(3 * DAY))).value()
            val titles = source.instances(day).value().map { it.title }
            expectEquals(listOf("Early", "Late"), titles, "titles in the day")
        },
        Scenario("an event that starts before the range but overlaps it is included") {
            source.create(draft("Overnight", Instant.parse("2026-10-05T23:30:00Z"))).value()
            expectEquals(1, source.instances(day).value().size, "overlapping instance")
        },
        Scenario("instances can be limited to some calendars") {
            source.create(draft("Mine")).value()
            expectEquals(
                0,
                source.instances(month, setOf(readOnly.id)).value().size,
                "other calendar"
            )
            expectEquals(
                1,
                source.instances(month, setOf(writable.id)).value().size,
                "own calendar"
            )
        },
        Scenario("update changes the event and its instances") {
            val id = source.create(draft()).value()
            source.update(source.event(id).value().copy(title = "Dinner")).value()
            expectEquals("Dinner", source.event(id).value().title, "title")
            expectEquals(
                listOf("Dinner"),
                source.instances(month).value().map {
                    it.title
                },
                "instance"
            )
        },
        Scenario("delete removes the event and a second delete finds nothing") {
            val id = source.create(draft()).value()
            source.delete(id).value()
            expectFailure(CalendarError.NotFound, source.event(id), "event after delete")
            expectFailure(CalendarError.NotFound, source.delete(id), "second delete")
            expectEquals(0, source.instances(month).value().size, "instances after delete")
        },
        Scenario("a read-only calendar refuses changes") {
            expectFailure(
                CalendarError.ReadOnly,
                source.create(draft().copy(calendarId = readOnly.id)),
                "create in read-only"
            )
        },
        Scenario("responding changes my status in the event and its instances") {
            val id = source.create(draft(attendees = listOf(Attendee.of(me)))).value()
            expectEquals(
                AttendeeStatus.NEEDS_ACTION,
                source.instances(month).value().single().selfStatus,
                "status before"
            )
            source.respond(id, AttendeeStatus.ACCEPTED).value()
            val mine = source.event(id).value().attendees.single { it.isOneOf(listOf(me)) }
            expectEquals(AttendeeStatus.ACCEPTED, mine.status, "status in event")
            expectEquals(
                AttendeeStatus.ACCEPTED,
                source.instances(month).value().single().selfStatus,
                "status in instance"
            )
        },
        Scenario("responding when I am not an attendee is invalid") {
            val id = source.create(
                draft(attendees = listOf(Attendee.of("ana@example.com")))
            ).value()
            expectFailure(
                CalendarError.Invalid("not an attendee"),
                source.respond(id, AttendeeStatus.ACCEPTED),
                "respond as a stranger"
            )
        },
        Scenario("a weekly series gives one instance per occurrence") {
            source.create(draft(rrule = "FREQ=WEEKLY;COUNT=3")).value()
            val instances = source.instances(month).value()
            expectEquals(3, instances.size, "occurrences")
            expect(instances.all { it.isRecurring }, "all flagged as recurring")
            expectEquals(
                listOf(noon, noon.plusSeconds(WEEK), noon.plusSeconds(2 * WEEK)),
                instances.map { it.time.startIn(ZoneOffset.UTC) },
                "occurrence starts"
            )
        },
        Scenario("cancelling one occurrence leaves the others") {
            val id = source.create(draft(rrule = "FREQ=DAILY;COUNT=3")).value()
            source.cancelInstance(id, noon.plusSeconds(DAY)).value()
            expectEquals(
                listOf(noon, noon.plusSeconds(2 * DAY)),
                source.instances(month).value().map { it.time.startIn(ZoneOffset.UTC) },
                "remaining occurrences"
            )
            expectEquals(true, source.event(id).value().isRecurring, "series intact")
        },
        Scenario("editing one occurrence changes only that one") {
            val id = source.create(draft(rrule = "FREQ=DAILY;COUNT=3")).value()
            val second = noon.plusSeconds(DAY)
            source.editInstance(id, second, draft("Moved", second)).value()
            val instances = source.instances(month).value()
            expectEquals(listOf("Lunch", "Moved", "Lunch"), instances.map { it.title }, "titles")
            expect(instances.all { it.isRecurring }, "still part of the series")
        },
        Scenario("changing an occurrence that does not exist finds nothing") {
            val id = source.create(draft(rrule = "FREQ=DAILY;COUNT=2")).value()
            expectFailure(
                CalendarError.NotFound,
                source.cancelInstance(id, noon.plusSeconds(10 * DAY)),
                "cancel unknown occurrence"
            )
        },
        Scenario("search finds an event by title, location, description or attendee") {
            source.create(draft("Budget review")).value()
            source.create(draft("Lunch").copy(location = "Harbour cafe")).value()
            source.create(draft("Call").copy(description = "Discuss the roadmap")).value()
            source.create(
                draft("Visit", attendees = listOf(Attendee.of("zoe@example.com", "Zoe Smith")))
            ).value()
            expectEquals(listOf("Budget review"), source.titlesFor("budget"), "title")
            expectEquals(listOf("Lunch"), source.titlesFor("harbour"), "location")
            expectEquals(listOf("Call"), source.titlesFor("roadmap"), "description")
            expectEquals(listOf("Visit"), source.titlesFor("smith"), "attendee name")
            expectEquals(listOf("Visit"), source.titlesFor("zoe@example"), "attendee address")
            expectEquals(emptyList(), source.titlesFor("nothing like this"), "no match")
        },
        Scenario("search ignores case and accents in both directions") {
            source.create(draft("Reunión semanal")).value()
            source.create(draft("Cafe con Zoë")).value()
            expectEquals(
                listOf("Reunión semanal"),
                source.titlesFor("REUNION"),
                "plain finds accent"
            )
            expectEquals(
                listOf("Reunión semanal"),
                source.titlesFor("reunión"),
                "accent finds accent"
            )
            expectEquals(listOf("Cafe con Zoë"), source.titlesFor("zoe"), "plain finds diaeresis")
            expectEquals(listOf("Cafe con Zoë"), source.titlesFor("café"), "accent finds plain")
        },
        Scenario("search needs every word, wherever each one is") {
            source.create(draft("Planning").copy(location = "Room 4")).value()
            source.create(draft("Planning").copy(location = "Room 9")).value()
            expectEquals(
                listOf("Planning"),
                source.titlesFor("room 4 planning"),
                "words in two fields"
            )
            expectEquals(emptyList(), source.titlesFor("planning room 5"), "one word missing")
        },
        Scenario("search takes percent, underscore and backslash literally") {
            source.create(draft("100% done")).value()
            source.create(draft("Plan_B")).value()
            source.create(draft("PlanXB")).value()
            source.create(draft("C:\\temp")).value()
            expectEquals(listOf("100% done"), source.titlesFor("100%"), "percent")
            expectEquals(listOf("Plan_B"), source.titlesFor("plan_b"), "underscore")
            expectEquals(listOf("Plan_B"), source.titlesFor("_"), "lone underscore")
            expectEquals(listOf("100% done"), source.titlesFor("%"), "lone percent")
            expectEquals(listOf("C:\\temp"), source.titlesFor("\\"), "backslash")
        },
        Scenario("search is limited to some calendars and to a range") {
            source.create(draft("Standup")).value()
            source.create(draft("Standup", noon.plusSeconds(60 * DAY))).value()
            expectEquals(2, source.search("standup").value().size, "all calendars, any time")
            expectEquals(
                0,
                source.search("standup", setOf(readOnly.id)).value().size,
                "other calendar"
            )
            expectEquals(0, source.search("standup", emptySet()).value().size, "no calendars")
            expectEquals(1, source.search("standup", range = month).value().size, "range")
        },
        Scenario("search lists a series once, found by a later occurrence in the range too") {
            source.create(draft("Gym", rrule = "FREQ=WEEKLY;COUNT=3")).value()
            val later =
                TimeRange(noon.plusSeconds(2 * WEEK - HOUR), noon.plusSeconds(2 * WEEK + HOUR))
            val found = source.search("gym").value().single()
            expect(found.isRecurring, "the series is flagged as recurring")
            expectEquals(1, source.search("gym", range = later).value().size, "third occurrence")
            expectEquals(
                0,
                source.search(
                    "gym",
                    range = TimeRange(noon.plusSeconds(3 * WEEK), noon.plusSeconds(4 * WEEK))
                )
                    .value()
                    .size,
                "after the series"
            )
        },
        Scenario("search gives the attendees and times of what it finds") {
            val dates = EventTime.AllDay(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7))
            source.create(
                draft("Retreat", attendees = listOf(Attendee.of("zoe@example.com", "Zoe")))
                    .copy(time = dates, color = 0xFF112233.toInt())
            ).value()
            val found = source.search("retreat").value().single()
            expectEquals(dates, found.time, "time")
            expectEquals(writable.id, found.calendarId, "calendar")
            expect(found.attendees.any { it.email == "zoe@example.com" }, "attendees come along")
        },
        Scenario("search does not find deleted events, blank queries or lone changed occurrences") {
            val id = source.create(draft("Doomed")).value()
            source.delete(id).value()
            expectEquals(0, source.search("doomed").value().size, "deleted")
            expectEquals(0, source.search("   ").value().size, "blank")
            val series = source.create(draft("Stand", rrule = "FREQ=DAILY;COUNT=2")).value()
            source.editInstance(
                series,
                noon.plusSeconds(DAY),
                draft("Moved", noon.plusSeconds(DAY))
            ).value()
            expectEquals(emptyList(), source.titlesFor("moved"), "changed occurrence alone")
            expectEquals(listOf("Stand"), source.titlesFor("stand"), "the series")
        },
        Scenario("instances with reminders carry the reminders and notes of their event") {
            val dates = EventTime.AllDay(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8))
            source.create(
                draft("Call").copy(
                    location = "Room 1",
                    description = "https://meet.example.com/abc",
                    reminders = listOf(Reminder(10), Reminder(60, ReminderMethod.EMAIL))
                )
            ).value()
            source.create(draft("Holiday").copy(time = dates, reminders = listOf(Reminder(0))))
                .value()
            source.create(draft("Plain", noon.plusSeconds(2 * HOUR))).value()
            val found = source.instancesWithReminders(month).value().associateBy {
                it.instance.title
            }
            expectEquals(
                setOf(Reminder(10), Reminder(60, ReminderMethod.EMAIL)),
                found.getValue("Call").reminders.toSet(),
                "reminders with their methods"
            )
            expectEquals(
                "https://meet.example.com/abc",
                found.getValue("Call").description,
                "notes"
            )
            expectEquals("Room 1", found.getValue("Call").instance.location, "location")
            expect(!found.getValue("Call").usesDefaults, "no defaults asked for")
            expectEquals(dates, found.getValue("Holiday").instance.time, "all-day dates")
            expectEquals(
                listOf(Reminder(0)),
                found.getValue("Holiday").reminders,
                "all-day reminder"
            )
            expectEquals(emptyList(), found.getValue("Plain").reminders, "no reminders")
            expectEquals(null, found.getValue("Plain").description, "no notes")
        },
        Scenario("instances with reminders are the same occurrences as instances") {
            val series = source.create(draft("Series", rrule = "FREQ=DAILY;COUNT=4")).value()
            source.cancelInstance(series, noon.plusSeconds(DAY)).value()
            source.create(draft("Later", noon.plusSeconds(3 * HOUR))).value()
            source.create(draft("Next month", noon.plusSeconds(40 * DAY))).value()
            source.delete(source.create(draft("Gone")).value()).value()
            source.create(draft("Overnight", Instant.parse("2026-10-05T23:30:00Z"))).value()
            for (range in listOf(day, month)) {
                expectEquals(
                    source.instances(range).value(),
                    source.instancesWithReminders(range).value().map { it.instance },
                    "same occurrences in the range"
                )
            }
            expectEquals(
                source.instances(month, setOf(readOnly.id)).value(),
                source.instancesWithReminders(month, setOf(readOnly.id)).value()
                    .map { it.instance },
                "same occurrences in another calendar"
            )
            expectEquals(
                0,
                source.instancesWithReminders(month, emptySet()).value().size,
                "no calendars"
            )
        },
        Scenario("the reminders follow an update, and every occurrence of a series has them") {
            val id = source.create(
                draft(
                    "Standup",
                    rrule = "FREQ=DAILY;COUNT=3"
                ).copy(reminders = listOf(Reminder(15)))
            ).value()
            val all = source.instancesWithReminders(month).value()
            expectEquals(3, all.size, "occurrences")
            expect(all.all { it.reminders == listOf(Reminder(15)) }, "the same reminder in each")
            source.update(source.event(id).value().copy(reminders = listOf(Reminder(5))))
                .value()
            expect(
                source.instancesWithReminders(month).value()
                    .all { it.reminders == listOf(Reminder(5)) },
                "the new reminder in each"
            )
            source.update(source.event(id).value().copy(reminders = emptyList())).value()
            expect(
                source.instancesWithReminders(month).value().all { it.reminders.isEmpty() },
                "no reminders left"
            )
        },
        Scenario("a changed occurrence is read with the reminders it was changed with") {
            val id = source.create(
                draft(rrule = "FREQ=DAILY;COUNT=3").copy(reminders = listOf(Reminder(15)))
            ).value()
            val second = noon.plusSeconds(DAY)
            // The provider may also copy the series' reminders into a changed occurrence, so the
            // scenario only needs the ones it asked for to be there.
            source.editInstance(
                id,
                second,
                draft("Moved", second.plusSeconds(HOUR)).copy(reminders = listOf(Reminder(5)))
            ).value()
            val found = source.instancesWithReminders(month).value()
            expectEquals(
                listOf("Lunch", "Moved", "Lunch"),
                found.map { it.instance.title },
                "titles"
            )
            expectEquals(
                listOf(noon, second.plusSeconds(HOUR), noon.plusSeconds(2 * DAY)),
                found.map { it.instance.time.startIn(ZoneOffset.UTC) },
                "starts"
            )
            expectEquals(setOf(Reminder(15)), found[0].reminders.toSet(), "first keeps its own")
            expect(Reminder(5) in found[1].reminders, "the changed one has the new reminder")
            expectEquals(setOf(Reminder(15)), found[2].reminders.toSet(), "last keeps its own")
        },
        Scenario("changes are announced") {
            coroutineScope {
                val seen = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(CHANGE_TIMEOUT_MS) { source.changes.first() }
                }
                // A source that merges flows (Room invalidation) subscribes asynchronously; give it time.
                delay(SUBSCRIBE_DELAY_MS)
                source.create(draft()).value()
                seen.await()
            }
        }
    )

    private const val HOUR = 3_600L
    private const val DAY = 86_400L
    private const val WEEK = 7 * DAY
    private const val CHANGE_TIMEOUT_MS = 5_000L
    private const val SUBSCRIBE_DELAY_MS = 100L
}

private suspend fun CalendarSource.titlesFor(query: String): List<String> =
    search(query).value().map { it.title }.sorted()

private fun <T> CalendarResult<T>.value(): T = when (this) {
    is CalendarResult.Success -> value
    is CalendarResult.Failure -> throw AssertionError("Expected success but got $error")
}

private fun expect(condition: Boolean, what: String) {
    if (!condition) throw AssertionError("Expected: $what")
}

private fun <T> expectEquals(expected: T, actual: T, what: String) {
    if (expected != actual) throw AssertionError("$what: expected <$expected> but was <$actual>")
}

private fun expectFailure(expected: CalendarError, result: CalendarResult<*>, what: String) {
    val actual = (result as? CalendarResult.Failure)?.error
    if (actual != expected) {
        throw AssertionError("$what: expected failure <$expected> but was <$result>")
    }
}
