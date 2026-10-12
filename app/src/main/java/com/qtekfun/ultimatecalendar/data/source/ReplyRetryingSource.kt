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
 * an answer is stored it has [ReplyDelivery] follow it with a bounded check that survives the
 * process dying, so a reply the system held back, or given offline, is not lost. Nothing else
 * changes.
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
        if (result is CalendarResult.Success) follow(id)
        return result
    }

    private suspend fun follow(id: EventId) {
        val event = (delegate.event(id) as? CalendarResult.Success)?.value ?: return
        val calendars = (delegate.calendars() as? CalendarResult.Success)?.value ?: return
        calendars.firstOrNull { it.id == event.calendarId }?.let {
            delivery.follow(it.account)
        }
    }
}
