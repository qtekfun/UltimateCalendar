// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.domain.invitations.EventCopies
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.OwnAccounts
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** Where the copy of an invitation for another of the user's accounts is. */
sealed interface CopyLookup {
    /** The invited account's own event, the one an answer must be written to. */
    data class Found(val eventId: EventId) : CopyLookup

    /** The invited account has not received the event yet; [accounts] are the ones to sync. */
    data class Missing(val accounts: Set<CalendarAccount>) : CopyLookup

    /** The event, or the account it was addressed to, no longer exists: nothing to answer. */
    data object Gone : CopyLookup

    /** The source could not tell. */
    data class Failed(val error: CalendarError) : CopyLookup
}

/**
 * Finds, for an invitation that lives in another account's calendar, the event of the invited
 * account itself: the one with the same iCalendar UID and start in a calendar of that account.
 * Google honours an answer only from the account it is addressed to, so that copy is where it is
 * written. It reads the occurrences around the invitation in that account's calendars only, never
 * the whole phone.
 */
class OwnCopies @Inject constructor(private val source: CalendarSource, private val clock: Clock) {
    suspend fun find(key: InvitationKey): CopyLookup = when (val read = source.event(key.eventId)) {
        is CalendarResult.Success -> findIn(key, read.value)
        is CalendarResult.Failure -> failed(read.error)
    }

    private suspend fun findIn(key: InvitationKey, event: Event): CopyLookup =
        when (val read = source.calendars()) {
            is CalendarResult.Success ->
                lookAmong(event, OwnAccounts.calendarsOf(read.value, key.address))

            is CalendarResult.Failure -> CopyLookup.Failed(read.error)
        }

    private suspend fun lookAmong(event: Event, mine: List<CalendarInfo>): CopyLookup {
        val ids = mine.map { it.id }.toSet()
        return when {
            mine.isEmpty() -> CopyLookup.Gone

            else -> when (val read = source.instances(around(event), ids)) {
                is CalendarResult.Success -> copyIn(event, read.value, mine)
                is CalendarResult.Failure -> CopyLookup.Failed(read.error)
            }
        }
    }

    private suspend fun copyIn(
        event: Event,
        instances: List<EventInstance>,
        mine: List<CalendarInfo>
    ): CopyLookup {
        val identity = EventCopies.identity(event, clock.zone)
        val found = instances.map { it.eventId }.distinct().firstOrNull { sameEvent(it, identity) }
        return found?.let(CopyLookup::Found) ?: CopyLookup.Missing(mine.map { it.account }.toSet())
    }

    private fun failed(error: CalendarError): CopyLookup =
        if (error == CalendarError.NotFound) CopyLookup.Gone else CopyLookup.Failed(error)

    private suspend fun sameEvent(id: EventId, identity: String): Boolean =
        (source.event(id) as? CalendarResult.Success)?.value
            ?.let { EventCopies.identity(it, clock.zone) } == identity

    private fun around(event: Event): TimeRange = TimeRange(
        event.time.startIn(clock.zone).minus(MARGIN),
        event.time.endIn(clock.zone).plus(MARGIN)
    )

    private companion object {
        val MARGIN: Duration = Duration.ofDays(1)
    }
}
