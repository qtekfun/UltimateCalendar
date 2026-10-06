// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation

/**
 * The operations that express an intent (an answer, a cancelled occurrence) rather than a state,
 * applied again to the event the server just changed: the user's intent is not lost to a merge
 * that preferred the server's newer change. Applying one that already holds is a no-op.
 */
internal object OperationIntents {
    fun apply(event: IcsEvent, operation: QueuedOperation): IcsEvent = when (operation) {
        is QueuedOperation.Respond -> respond(event, operation)
        is QueuedOperation.CancelInstance -> cancel(event, operation)
        else -> event
    }

    private fun respond(event: IcsEvent, operation: QueuedOperation.Respond): IcsEvent {
        val key = operation.occurrence?.toKey()
            ?: return event.copy(
                series = event.series.copy(event = answered(event.series.event, operation))
            )
        val overrides = event.series.overrides.map {
            val replacement = it.replacement
            if (it.recurrenceId == key && replacement != null) {
                OccurrenceOverride(key, answered(replacement, operation))
            } else {
                it
            }
        }
        return event.copy(series = event.series.copy(overrides = overrides))
    }

    private fun answered(event: Event, operation: QueuedOperation.Respond): Event = event.copy(
        attendees = event.attendees.map {
            if (it.email ==
                Attendee.normalize(operation.email)
            ) {
                it.copy(status = operation.status)
            } else {
                it
            }
        }
    )

    private fun cancel(event: IcsEvent, operation: QueuedOperation.CancelInstance): IcsEvent {
        val key = operation.occurrence.toKey()
        val cancelled = OccurrenceOverride(key, null)
        val overrides = event.series.overrides
        val updated = if (overrides.any { it.recurrenceId == key }) {
            overrides.map { if (it.recurrenceId == key) cancelled else it }
        } else {
            overrides + cancelled
        }
        return event.copy(series = event.series.copy(overrides = updated))
    }
}
