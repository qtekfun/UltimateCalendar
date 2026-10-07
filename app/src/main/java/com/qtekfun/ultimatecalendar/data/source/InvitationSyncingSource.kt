// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.data.sync.InvitationSyncs
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The source the app writes through: after a write to an event with guests, or the user's own
 * answer, it asks [syncs] to have the event's account sync, so that the invitation is sent now
 * and not whenever the sync adapter next runs (RF-05, RF-06). Reads and writes that touch no
 * guest pass through untouched.
 *
 * What counts as touching guests: creating an event with guests; changing the title, time,
 * place, repetition, notes or guests of an event that has guests (or gets them, or loses them);
 * deleting an event with guests or cancelling or changing one of its occurrences; answering.
 * Colors, reminders and availability are not sent to anybody. The event is read before the write
 * because after a delete it is gone, and an update must be compared with what was stored.
 */
class InvitationSyncingSource(
    private val delegate: CalendarSource,
    private val syncs: InvitationSyncs
) : CalendarSource by delegate,
    ProviderAccess {
    /** The wrapped source's access state; a source that cannot tell is never denied. */
    override val denied: StateFlow<Boolean> =
        (delegate as? ProviderAccess)?.denied ?: MutableStateFlow(false).asStateFlow()

    override suspend fun create(draft: EventDraft): CalendarResult<EventId> =
        delegate.create(draft).also {
            if (it is CalendarResult.Success && draft.attendees.isNotEmpty()) {
                touched(draft.calendarId)
            }
        }

    override suspend fun update(event: Event): CalendarResult<Unit> {
        val before = stored(event.id)
        return delegate.update(event).also {
            if (it is CalendarResult.Success && sendsSomething(before, event)) {
                touched(event.calendarId)
            }
        }
    }

    override suspend fun delete(id: EventId): CalendarResult<Unit> {
        val before = stored(id)
        return delegate.delete(id).also {
            if (it is CalendarResult.Success && before?.attendees?.isNotEmpty() == true) {
                touched(before.calendarId)
            }
        }
    }

    override suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit> {
        val before = stored(id)
        return delegate.editInstance(id, originalStart, changes).also {
            if (it is CalendarResult.Success &&
                (before?.attendees?.isNotEmpty() == true || changes.attendees.isNotEmpty())
            ) {
                touched(before?.calendarId ?: changes.calendarId)
            }
        }
    }

    override suspend fun cancelInstance(id: EventId, originalStart: Instant): CalendarResult<Unit> {
        val before = stored(id)
        return delegate.cancelInstance(id, originalStart).also {
            if (it is CalendarResult.Success && before?.attendees?.isNotEmpty() == true) {
                touched(before.calendarId)
            }
        }
    }

    override suspend fun respond(id: EventId, status: AttendeeStatus): CalendarResult<Unit> {
        val before = stored(id)
        return delegate.respond(id, status).also {
            if (it is CalendarResult.Success && before != null) touched(before.calendarId)
        }
    }

    private suspend fun stored(id: EventId): Event? = delegate.event(id).getOrNull()

    /**
     * Whether [after] changes something the guests are told, in an event that has or had guests.
     * When the stored event could not be read, guests alone decide.
     */
    private fun sendsSomething(before: Event?, after: Event): Boolean = when {
        before == null -> after.attendees.isNotEmpty()

        before.attendees.isEmpty() && after.attendees.isEmpty() -> false

        else ->
            before.time != after.time ||
                before.title != after.title ||
                before.location != after.location ||
                before.description != after.description ||
                before.rrule != after.rrule ||
                before.attendees.toSet() != after.attendees.toSet()
    }

    private suspend fun touched(calendar: CalendarId) {
        val calendars = delegate.calendars().getOrNull() ?: return
        calendars.firstOrNull { it.id == calendar }?.let { syncs.written(it.account) }
    }
}
