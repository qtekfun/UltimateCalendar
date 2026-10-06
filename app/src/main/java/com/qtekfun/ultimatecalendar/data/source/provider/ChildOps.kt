// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Attendees
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.Reminder

/** The writes of an event's attendees and reminders, which the provider keeps in own tables. */
internal object ChildOps {
    /** Inserts for [eventId], or for the event inserted by the batch op [parentOp]. */
    fun inserts(
        attendees: List<Attendee>,
        reminders: List<Reminder>,
        eventId: Long?,
        parentOp: Int?
    ): List<ProviderOp> = attendees.map {
        ProviderOp.Insert(ProviderTable.ATTENDEES, AttendeeMapping.toValues(it, eventId), parentOp)
    } + reminders.map {
        ProviderOp.Insert(ProviderTable.REMINDERS, ReminderMapping.toValues(it, eventId), parentOp)
    }

    /** Replaces the attendees and the reminders of [eventId]; a null list is left untouched. */
    fun replace(
        eventId: Long,
        attendees: List<Attendee>?,
        reminders: List<Reminder>?
    ): List<ProviderOp> {
        val where = "${Attendees.EVENT_ID}=?"
        val args = listOf(eventId.toString())
        return buildList {
            if (attendees != null) {
                add(ProviderOp.Delete(ProviderTable.ATTENDEES, null, where, args))
                addAll(inserts(attendees, emptyList(), eventId, null))
            }
            if (reminders != null) {
                add(ProviderOp.Delete(ProviderTable.REMINDERS, null, where, args))
                addAll(inserts(emptyList(), reminders, eventId, null))
            }
        }
    }
}
