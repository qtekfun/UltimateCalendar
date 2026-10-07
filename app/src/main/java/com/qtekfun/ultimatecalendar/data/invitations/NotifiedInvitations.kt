// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.local.dao.NotifiedInvitationDao
import com.qtekfun.ultimatecalendar.data.local.entity.NotifiedInvitationEntity
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
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

/**
 * The invitations the user was already told about (RF-07), kept in Room so that only a new or
 * changed one notifies again, even after the process was killed.
 */
@Singleton
class NotifiedInvitations @Inject constructor(
    private val dao: NotifiedInvitationDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun load(): List<Invitation> = withContext(io) { dao.all().map { it.toInvitation() } }

    /** Replaces everything stored with [invitations], all or nothing. */
    suspend fun replaceAll(invitations: List<Invitation>) = withContext(io) {
        dao.replaceAll(invitations.map { it.toEntity() })
    }

    private fun Invitation.toEntity(): NotifiedInvitationEntity = when (val moment = time) {
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

    private fun Invitation.entity(allDay: Boolean, start: Long, end: Long, zone: String?) =
        NotifiedInvitationEntity(
            calendarId = key.calendarId.value,
            eventId = key.eventId.value,
            title = title,
            allDay = allDay,
            start = start,
            end = end,
            zone = zone,
            location = location,
            organizer = organizer,
            address = key.address,
            account = account
        )

    private fun NotifiedInvitationEntity.toInvitation() = Invitation(
        key = InvitationKey(CalendarId(calendarId), EventId(eventId), address),
        title = title,
        time = if (allDay) {
            EventTime.AllDay(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end))
        } else {
            EventTime.Timed(
                Instant.ofEpochMilli(start),
                Instant.ofEpochMilli(end),
                ZoneId.of(checkNotNull(zone) { "A timed invitation is stored with its zone" })
            )
        },
        location = location,
        organizer = organizer,
        account = account
    )
}
