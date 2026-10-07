// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.data.sync.ReplyDelivery
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The source every screen, the notification buttons included, answers invitations through: after
 * an answer is stored, and when its account cannot sync at that moment, it schedules a bounded
 * retry that survives the process dying ([ReplyDelivery]), so a reply given offline is not lost.
 * Nothing else changes.
 */
class ReplyRetryingSource(
    private val delegate: CalendarSource,
    private val delivery: ReplyDelivery
) : CalendarSource by delegate,
    ProviderAccess {
    override val denied: StateFlow<Boolean> =
        (delegate as? ProviderAccess)?.denied ?: MutableStateFlow(false).asStateFlow()

    override suspend fun respond(id: EventId, status: AttendeeStatus): CalendarResult<Unit> {
        val result = delegate.respond(id, status)
        if (result is CalendarResult.Success) scheduleIfWaiting(id)
        return result
    }

    private suspend fun scheduleIfWaiting(id: EventId) {
        val event = (delegate.event(id) as? CalendarResult.Success)?.value ?: return
        val calendars = (delegate.calendars() as? CalendarResult.Success)?.value ?: return
        calendars.firstOrNull { it.id == event.calendarId }?.let {
            delivery.retryIfWaiting(it.account)
        }
    }
}
