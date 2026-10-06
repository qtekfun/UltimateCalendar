// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Events
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import java.time.Instant

/**
 * Changes or cancels one occurrence of a series. The provider keeps such a change as an
 * exception event linked to the series (`CONTENT_EXCEPTION_URI`); a second change of the same
 * occurrence finds that event and reuses it.
 */
internal class SeriesExceptions(private val store: ProviderStore) {
    /** Cancels the occurrence at [originalStart], or changes it to [edit] when it is not null. */
    fun change(id: EventId, originalStart: Instant, edit: EventDraft?) {
        val series = store.eventRow(id)
        store.editableCalendar(series)
        if (!EventMapping.repeats(series)) abort(CalendarError.Invalid("not a series"))
        val originalMs = originalStart.toEpochMilli()
        val existing = store.exceptionOf(id, originalMs)
        if (existing == null && !store.hasInstance(id, originalMs)) abort(CalendarError.NotFound)
        val values = valuesOf(series, originalMs, edit)
        store.write(
            if (existing == null) {
                listOf<ProviderOp>(ProviderOp.InsertException(id.value, values)) +
                    ChildOps.inserts(edit?.attendees.orEmpty(), edit?.reminders.orEmpty(), null, 0)
            } else {
                listOf<ProviderOp>(ProviderOp.Update(ProviderTable.EVENTS, existing, values)) +
                    edit?.let { ChildOps.replace(existing, it.attendees, it.reminders) }.orEmpty()
            }
        )
    }

    private fun valuesOf(series: ProviderRow, originalMs: Long, edit: EventDraft?): ProviderRow =
        if (edit == null) {
            mapOf(
                Events.STATUS to Events.STATUS_CANCELED,
                Events.ORIGINAL_INSTANCE_TIME to originalMs
            )
        } else {
            EventMapping.toValues(edit.copy(rrule = null)) + mapOf(
                Events.CALENDAR_ID to series.long(Events.CALENDAR_ID),
                Events.STATUS to Events.STATUS_CONFIRMED,
                Events.ORIGINAL_INSTANCE_TIME to originalMs
            )
        }
}
