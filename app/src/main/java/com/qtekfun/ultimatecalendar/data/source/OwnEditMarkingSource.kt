// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.data.invitations.OwnEditMarks
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant

/**
 * The source the app's own screens write through: before changing or deleting an event it flags
 * it in the record of events the user goes to ([OwnEditMarks]), so that the invitation check does
 * not tell the user about a change they made themselves (RF-07). The flag is set before the write
 * because the provider announces the change as soon as it is made; a failed write takes it back.
 */
class OwnEditMarkingSource(private val delegate: CalendarSource, private val marks: OwnEditMarks) :
    CalendarSource by delegate {
    override suspend fun update(event: Event): CalendarResult<Unit> =
        marked(event.id) { delegate.update(event) }

    override suspend fun delete(id: EventId): CalendarResult<Unit> =
        marked(id) { delegate.delete(id) }

    override suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit> = marked(id) { delegate.editInstance(id, originalStart, changes) }

    override suspend fun cancelInstance(id: EventId, originalStart: Instant): CalendarResult<Unit> =
        marked(id) { delegate.cancelInstance(id, originalStart) }

    private suspend fun marked(
        id: EventId,
        write: suspend () -> CalendarResult<Unit>
    ): CalendarResult<Unit> {
        marks.mark(id)
        val result = write()
        if (result is CalendarResult.Failure) marks.mark(id, marked = false)
        return result
    }
}
