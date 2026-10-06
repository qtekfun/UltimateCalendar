// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.conflict

import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.recurrence.EventSeries
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

internal fun timed(start: String, end: String, zone: ZoneId = MADRID) =
    EventTime.Timed(Instant.parse(start), Instant.parse(end), zone)

internal fun event(
    title: String = "Standup",
    time: EventTime = timed("2026-10-05T07:00:00Z", "2026-10-05T07:30:00Z"),
    description: String? = null,
    location: String? = null,
    rrule: String? = null,
    availability: Availability = Availability.BUSY,
    organizer: String? = null,
    attendees: List<Attendee> = emptyList(),
    reminders: List<Reminder> = emptyList(),
    exDates: Set<OccurrenceKey> = emptySet(),
    rDates: Set<OccurrenceKey> = emptySet(),
    overrides: List<OccurrenceOverride> = emptyList()
) = IcsEvent(
    uid = "uid-1",
    status = com.qtekfun.ultimatecalendar.data.ical.EventStatus.CONFIRMED,
    sequence = 0,
    series = EventSeries(
        event = Event(
            id = EventId(1),
            calendarId = CalendarId(1),
            title = title,
            time = time,
            description = description,
            location = location,
            availability = availability,
            rrule = rrule,
            organizer = organizer,
            attendees = attendees,
            reminders = reminders
        ),
        exDates = exDates,
        rDates = rDates,
        overrides = overrides
    )
)

private val T0: Instant = Instant.parse("2026-10-01T10:00:00Z")
private val EARLIER: Instant = T0.minusSeconds(60)
private val LATER: Instant = T0.plusSeconds(60)

class ConflictResolverTest {
    private val base = event()

    private fun local(event: IcsEvent, vararg dirty: EventField, at: Instant? = LATER) =
        LocalVersion(event, dirty.toSet(), at)

    private fun server(event: IcsEvent?, at: Instant? = T0) = ServerVersion(event, at)

    private fun merge(base: IcsEvent?, local: LocalVersion, server: ServerVersion) =
        ConflictResolver.resolve(base, local, server) as Resolution.Merge

    @Nested
    inner class DeletedOnTheServer {
        @Test
        fun `unchanged here, it is deleted here too`() {
            assertEquals(
                Resolution.DeleteLocally,
                ConflictResolver.resolve(base, local(base), server(null, null))
            )
        }

        @Test
        fun `changed here, the user chooses and the local event is kept`() {
            val edited = event(title = "Retro")
            assertEquals(
                Resolution.DeletedOnServer(edited),
                ConflictResolver.resolve(base, local(edited, EventField.TITLE), server(null, null))
            )
        }
    }

    @Nested
    inner class OneSideChanged {
        @Test
        fun `a field changed only on the server takes the server value`() {
            val onServer = event(location = "Room 2")
            val result = merge(base, local(base), server(onServer))
            assertEquals(onServer, result.event)
            assertEquals(emptySet<EventField>(), result.push)
        }

        @Test
        fun `fields not marked as changed here take the server value even when different`() {
            val edited = event(title = "Retro", location = "Hall")
            val onServer = event(location = "Room 2")
            val result = merge(base, local(edited, EventField.TITLE), server(onServer))
            assertEquals(event(title = "Retro", location = "Room 2"), result.event)
            assertEquals(setOf(EventField.TITLE), result.push)
        }

        @Test
        fun `a field changed only here is kept and sent, even if the server is newer`() {
            val edited = event(availability = Availability.FREE)
            val onServer = event(title = "Standup", location = "Room 2")
            val result =
                merge(base, local(edited, EventField.AVAILABILITY, at = EARLIER), server(onServer))
            assertEquals(event(availability = Availability.FREE, location = "Room 2"), result.event)
            assertEquals(setOf(EventField.AVAILABILITY), result.push)
            assertEquals(emptyList<TextConflict>(), result.conflicts)
        }

        @Test
        fun `a change that both sides made the same way needs nothing`() {
            val same = event(time = timed("2026-10-05T08:00:00Z", "2026-10-05T09:00:00Z"))
            val result = merge(base, local(same, EventField.TIME), server(same))
            assertEquals(same, result.event)
            assertEquals(emptySet<EventField>(), result.push)
        }

        @Test
        fun `a text changed only here is sent without asking`() {
            val edited = event(description = "Agenda attached")
            val result = merge(base, local(edited, EventField.DESCRIPTION), server(base))
            assertEquals(edited, result.event)
            assertEquals(setOf(EventField.DESCRIPTION), result.push)
            assertEquals(emptyList<TextConflict>(), result.conflicts)
        }
    }

    @Nested
    inner class BothSidesChanged {
        private val localMove = event(time = timed("2026-10-05T08:00:00Z", "2026-10-05T08:30:00Z"))
        private val serverMove = event(time = timed("2026-10-05T09:00:00Z", "2026-10-05T09:30:00Z"))

        @Test
        fun `the later change wins - local newer is sent`() {
            val result =
                merge(base, local(localMove, EventField.TIME, at = LATER), server(serverMove, T0))
            assertEquals(localMove, result.event)
            assertEquals(setOf(EventField.TIME), result.push)
        }

        @Test
        fun `the later change wins - server newer is kept and nothing is sent`() {
            val result =
                merge(base, local(localMove, EventField.TIME, at = EARLIER), server(serverMove, T0))
            assertEquals(serverMove, result.event)
            assertEquals(emptySet<EventField>(), result.push)
        }

        @Test
        fun `a tie keeps the local change`() {
            val result =
                merge(base, local(localMove, EventField.TIME, at = T0), server(serverMove, T0))
            assertEquals(localMove, result.event)
            assertEquals(setOf(EventField.TIME), result.push)
        }

        @Test
        fun `an unknown server time keeps the local change`() {
            val result =
                merge(
                    base,
                    local(localMove, EventField.TIME, at = EARLIER),
                    server(serverMove, null)
                )
            assertEquals(localMove, result.event)
            assertEquals(setOf(EventField.TIME), result.push)
        }

        @Test
        fun `an unknown local time loses to a known server time`() {
            val result =
                merge(base, local(localMove, EventField.TIME, at = null), server(serverMove, T0))
            assertEquals(serverMove, result.event)
            assertEquals(emptySet<EventField>(), result.push)
        }

        @Test
        fun `without a base every dirty field counts as changed on the server`() {
            val newer =
                merge(null, local(localMove, EventField.TIME, at = LATER), server(serverMove, T0))
            assertEquals(localMove, newer.event)
            assertEquals(setOf(EventField.TIME), newer.push)
            val older =
                merge(null, local(localMove, EventField.TIME, at = EARLIER), server(serverMove, T0))
            assertEquals(serverMove, older.event)
            assertEquals(emptySet<EventField>(), older.push)
        }

        @Test
        fun `a newer server change wins every field it shares and brings its details`() {
            val edited = event(
                time = localMove.series.event.time,
                availability = Availability.FREE
            )
            val onServer = event(
                time = serverMove.series.event.time,
                availability = Availability.TENTATIVE
            )
            val result = merge(
                base,
                local(edited, EventField.TIME, EventField.AVAILABILITY, at = T0.minusSeconds(1)),
                server(onServer.copy(sequence = 4), T0)
            )
            assertEquals(onServer.copy(sequence = 4), result.event)
            assertEquals(emptySet<EventField>(), result.push)
        }

        @Test
        fun `a time that differs only by zone is a change`() {
            val inLondon = event(
                time = timed(
                    "2026-10-05T07:00:00Z",
                    "2026-10-05T07:30:00Z",
                    ZoneId.of("Europe/London")
                )
            )
            val result = merge(base, local(inLondon, EventField.TIME), server(base))
            assertEquals(inLondon, result.event)
            assertEquals(setOf(EventField.TIME), result.push)
        }

        @Test
        fun `a move across the end of daylight saving time is compared as instants`() {
            // Madrid goes back from CEST to CET on 2026-10-25 at 01:00Z: the hour between repeats.
            val before = event(time = timed("2026-10-24T22:30:00Z", "2026-10-24T23:30:00Z"))
            val after = event(time = timed("2026-10-25T01:30:00Z", "2026-10-25T02:30:00Z"))
            val result = merge(before, local(after, EventField.TIME), server(before))
            assertEquals(after, result.event)
            assertEquals(setOf(EventField.TIME), result.push)
        }
    }

    @Nested
    inner class Text {
        @Test
        fun `a text changed on both sides is never overwritten, the user is asked`() {
            val edited = event(title = "Retro")
            val onServer = event(title = "Planning")
            val result = merge(base, local(edited, EventField.TITLE, at = LATER), server(onServer))
            assertEquals(edited, result.event)
            assertEquals(emptySet<EventField>(), result.push)
            assertEquals(
                listOf(TextConflict(EventField.TITLE, "Retro", "Planning")),
                result.conflicts
            )
        }

        @Test
        fun `it asks even when the server change is the newer one`() {
            val result = merge(
                base,
                local(event(location = "Hall"), EventField.LOCATION, at = EARLIER),
                server(event(location = "Room 2"))
            )
            assertEquals(event(location = "Hall"), result.event)
            assertEquals(
                listOf(TextConflict(EventField.LOCATION, "Hall", "Room 2")),
                result.conflicts
            )
        }

        @Test
        fun `a text emptied on one side is also a conflict`() {
            val result = merge(
                event(description = "Old"),
                local(event(description = null), EventField.DESCRIPTION),
                server(event(description = "New"))
            )
            assertEquals(
                listOf(TextConflict(EventField.DESCRIPTION, null, "New")),
                result.conflicts
            )
            val reverse = merge(
                event(description = "Old"),
                local(event(description = "Mine"), EventField.DESCRIPTION),
                server(event(description = null))
            )
            assertEquals(
                listOf(TextConflict(EventField.DESCRIPTION, "Mine", null)),
                reverse.conflicts
            )
        }

        @Test
        fun `without a base two different texts are a conflict`() {
            val result = merge(
                null,
                local(event(title = "Retro"), EventField.TITLE),
                server(event(title = "Planning"))
            )
            assertEquals(1, result.conflicts.size)
        }

        @Test
        fun `the same text on both sides is no conflict`() {
            val result = merge(
                base,
                local(event(title = "Retro"), EventField.TITLE),
                server(event(title = "Retro"))
            )
            assertEquals(emptyList<TextConflict>(), result.conflicts)
            assertEquals(emptySet<EventField>(), result.push)
        }
    }

    @Nested
    inner class Overrides {
        private val monday = OccurrenceKey.Moment(Instant.parse("2026-10-05T07:00:00Z"))
        private val tuesday = OccurrenceKey.Moment(Instant.parse("2026-10-06T07:00:00Z"))
        private val wednesday = OccurrenceKey.Moment(Instant.parse("2026-10-07T07:00:00Z"))
        private val allDay = OccurrenceKey.Day(LocalDate.parse("2026-10-08"))

        private fun series(vararg overrides: OccurrenceOverride) =
            event(rrule = "FREQ=DAILY", overrides = overrides.toList())

        private fun cancelled(key: OccurrenceKey) = OccurrenceOverride(key, null)

        private fun moved(key: OccurrenceKey, title: String) =
            OccurrenceOverride(key, event(title = title).series.event)

        private fun overridesOf(result: Resolution.Merge) = result.event.series.overrides.toSet()

        @Test
        fun `an occurrence cancelled here and another edited on the server both survive`() {
            val result = merge(
                series(),
                local(series(cancelled(monday)), EventField.OVERRIDES),
                server(series(moved(tuesday, "Late start")))
            )
            assertEquals(
                setOf(moved(tuesday, "Late start"), cancelled(monday)),
                overridesOf(result)
            )
            assertEquals(setOf(EventField.OVERRIDES), result.push)
        }

        @Test
        fun `a cancellation can be pushed after the server cancelled another one`() {
            val result = merge(
                series(cancelled(allDay)),
                local(series(cancelled(allDay), cancelled(monday)), EventField.OVERRIDES),
                server(series(cancelled(allDay), cancelled(tuesday)))
            )
            assertEquals(
                setOf(cancelled(allDay), cancelled(tuesday), cancelled(monday)),
                overridesOf(result)
            )
            assertEquals(
                listOf(allDay, tuesday, monday),
                result.event.series.overrides.map {
                    it.recurrenceId
                }
            )
        }

        @Test
        fun `only the server changed an occurrence - its version wins and nothing is sent`() {
            val result = merge(
                series(moved(monday, "A")),
                local(series(moved(monday, "A")), EventField.OVERRIDES),
                server(series(moved(monday, "B")))
            )
            assertEquals(setOf(moved(monday, "B")), overridesOf(result))
            assertEquals(emptySet<EventField>(), result.push)
        }

        @Test
        fun `only the server removed an occurrence override - it is gone and nothing is sent`() {
            val result = merge(
                series(moved(monday, "A"), cancelled(tuesday)),
                local(
                    series(moved(monday, "A"), cancelled(tuesday), cancelled(wednesday)),
                    EventField.OVERRIDES
                ),
                server(series(cancelled(tuesday)))
            )
            assertEquals(setOf(cancelled(tuesday), cancelled(wednesday)), overridesOf(result))
            assertEquals(setOf(EventField.OVERRIDES), result.push)
        }

        @Test
        fun `only here an occurrence override was removed - the removal is sent`() {
            val result = merge(
                series(moved(monday, "A")),
                local(series(), EventField.OVERRIDES),
                server(series(moved(monday, "A")))
            )
            assertEquals(emptySet<OccurrenceOverride>(), overridesOf(result))
            assertEquals(setOf(EventField.OVERRIDES), result.push)
        }

        @Test
        fun `both sides changed the same occurrence - the later one wins`() {
            val mine = local(
                series(cancelled(monday)),
                EventField.OVERRIDES,
                at = LATER
            )
            val newer =
                merge(series(moved(monday, "A")), mine, server(series(moved(monday, "B")), T0))
            assertEquals(setOf(cancelled(monday)), overridesOf(newer))
            assertEquals(setOf(EventField.OVERRIDES), newer.push)

            val older = merge(
                series(moved(monday, "A")),
                mine.copy(changedAt = EARLIER),
                server(series(moved(monday, "B")), T0)
            )
            assertEquals(setOf(moved(monday, "B")), overridesOf(older))
            assertEquals(emptySet<EventField>(), older.push)
        }

        @Test
        fun `both sides changed the same occurrence even by removing it here`() {
            val result = merge(
                series(moved(monday, "A")),
                local(series(), EventField.OVERRIDES, at = LATER),
                server(series(moved(monday, "B")))
            )
            assertEquals(emptySet<OccurrenceOverride>(), overridesOf(result))
            assertEquals(setOf(EventField.OVERRIDES), result.push)
        }

        @Test
        fun `without a base the later change wins for every differing occurrence`() {
            val mine = local(series(moved(monday, "A"), cancelled(tuesday)), EventField.OVERRIDES)
            val onServer = server(series(moved(monday, "B")), T0)
            val newer = merge(null, mine, onServer)
            assertEquals(setOf(moved(monday, "A"), cancelled(tuesday)), overridesOf(newer))
            assertEquals(setOf(EventField.OVERRIDES), newer.push)

            val older = merge(null, mine.copy(changedAt = EARLIER), onServer)
            assertEquals(setOf(moved(monday, "B")), overridesOf(older))
            assertEquals(emptySet<EventField>(), older.push)
        }

        @Test
        fun `the same overrides in another order are the same`() {
            val result = merge(
                series(),
                local(series(cancelled(monday), cancelled(tuesday)), EventField.OVERRIDES),
                server(series(cancelled(tuesday), cancelled(monday)))
            )
            assertEquals(setOf(cancelled(monday), cancelled(tuesday)), overridesOf(result))
            assertEquals(emptySet<EventField>(), result.push)
            assertEquals(
                listOf(tuesday, monday),
                result.event.series.overrides.map {
                    it.recurrenceId
                }
            )
        }
    }

    @Nested
    inner class Fields {
        private val monday = OccurrenceKey.Moment(Instant.parse("2026-10-05T07:00:00Z"))
        private val guest = Attendee.of("bo@example.com", status = AttendeeStatus.ACCEPTED)
        private val changed = event(
            title = "T",
            description = "D",
            location = "L",
            time = timed("2026-10-05T10:00:00Z", "2026-10-05T11:00:00Z"),
            rrule = "FREQ=DAILY",
            availability = Availability.FREE,
            organizer = "ana@example.com",
            attendees = listOf(guest),
            reminders = listOf(Reminder(10)),
            exDates = setOf(monday),
            rDates = setOf(OccurrenceKey.Moment(Instant.parse("2026-10-09T07:00:00Z"))),
            overrides = listOf(OccurrenceOverride(monday, null))
        )

        @Test
        fun `each field reads and writes only itself`() {
            EventField.entries.forEach { field ->
                val written = field.write(base, changed)
                assertEquals(field.read(changed), field.read(written), field.name)
                EventField.entries.filter { it != field }.forEach { other ->
                    assertEquals(
                        other.read(base),
                        other.read(written),
                        "${field.name} touched ${other.name}"
                    )
                }
            }
        }

        @Test
        fun `each field has its own bit and round trips through them`() {
            assertEquals(EventField.entries.size, EventField.entries.map { it.bit }.toSet().size)
            val all = EventField.entries.toSet()
            assertEquals(all, EventField.fromBits(EventField.toBits(all)))
            assertEquals(emptySet<EventField>(), EventField.fromBits(0))
            assertEquals(0, EventField.toBits(emptySet()))
            assertEquals(
                setOf(EventField.TIME, EventField.OVERRIDES),
                EventField.fromBits(EventField.TIME.bit or EventField.OVERRIDES.bit)
            )
        }

        @Test
        fun `only title, description and location are text`() {
            assertEquals(
                setOf(EventField.TITLE, EventField.DESCRIPTION, EventField.LOCATION),
                EventField.entries.filter { it.isText }.toSet()
            )
            assertTrue(EventField.entries.none { it.isText && it == EventField.OVERRIDES })
        }
    }
}
