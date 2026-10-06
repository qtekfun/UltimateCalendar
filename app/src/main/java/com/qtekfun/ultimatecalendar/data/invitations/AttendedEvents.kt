// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.local.dao.AttendedEventDao
import com.qtekfun.ultimatecalendar.data.local.entity.AttendedEventEntity
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.invitations.AttendedEvent
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What the invitation check keeps of the events the user goes to (RF-07). */
interface AttendedEventRecord {
    suspend fun load(): List<AttendedEvent>

    /** Replaces everything stored with [events], all or nothing. */
    suspend fun replaceAll(events: List<AttendedEvent>)
}

/** Tells the record that this app is about to change an event, so the check stays quiet. */
interface OwnEditMarks {
    /** Flags the event (or, with [marked] false, takes the flag back after a failed change). */
    suspend fun mark(eventId: EventId, marked: Boolean = true)
}

/** Keeps nothing: for a checker built without the record. */
object NoAttendedEvents : AttendedEventRecord {
    override suspend fun load(): List<AttendedEvent> = emptyList()

    override suspend fun replaceAll(events: List<AttendedEvent>) = Unit
}

/**
 * The events the user goes to, in Room: each with the minimum to tell a change (see
 * [AttendedEvent]). Only upcoming events are kept: every check replaces the whole set, so past
 * events are pruned as it goes.
 */
@Singleton
class AttendedEvents @Inject constructor(
    private val dao: AttendedEventDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : AttendedEventRecord,
    OwnEditMarks {
    override suspend fun load(): List<AttendedEvent> =
        withContext(io) { dao.all().map { it.toEvent() } }

    override suspend fun replaceAll(events: List<AttendedEvent>) = withContext(io) {
        dao.replaceAll(events.map { it.toEntity() })
    }

    override suspend fun mark(eventId: EventId, marked: Boolean) = withContext(io) {
        dao.markOwnEdit(eventId.value, marked)
    }

    private fun AttendedEvent.toEntity(): AttendedEventEntity = when (val moment = time) {
        is EventTime.Timed -> entity(
            allDay = false,
            start = moment.start.toEpochMilli(),
            end = moment.end.toEpochMilli(),
            zone = moment.zone.id
        )

        is EventTime.AllDay -> entity(
            allDay = true,
            start = moment.startDate.toEpochDay(),
            end = moment.endDate.toEpochDay(),
            zone = null
        )
    }

    private fun AttendedEvent.entity(allDay: Boolean, start: Long, end: Long, zone: String?) =
        AttendedEventEntity(
            calendarId = key.calendarId.value,
            eventId = key.eventId.value,
            title = title,
            allDay = allDay,
            start = start,
            end = end,
            zone = zone,
            placeHash = placeHash,
            ownEdit = ownEdit
        )

    private fun AttendedEventEntity.toEvent() = AttendedEvent(
        key = InvitationKey(CalendarId(calendarId), EventId(eventId)),
        title = title,
        time = if (allDay) {
            EventTime.AllDay(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end))
        } else {
            EventTime.Timed(
                Instant.ofEpochMilli(start),
                Instant.ofEpochMilli(end),
                ZoneId.of(checkNotNull(zone) { "A timed event is stored with its zone" })
            )
        },
        placeHash = placeHash,
        ownEdit = ownEdit
    )
}
