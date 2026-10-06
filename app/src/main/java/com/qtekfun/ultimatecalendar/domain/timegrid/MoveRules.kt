// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope

/**
 * What can be done to the events of a calendar by dragging them (T18). [canEdit] is the
 * calendar's access; [canMoveOne] is false where changing a single occurrence of a series is
 * unsafe: in an on-device `LOCAL` account a series without `_SYNC_ID` loses all its instances as
 * soon as one occurrence is changed (SPEC §9, T05).
 */
data class MoveRule(val canEdit: Boolean, val canMoveOne: Boolean) {
    /** The scopes to offer for a repeating event of this calendar, in the order to show them. */
    val scopes: List<RecurrenceScope>
        get() = RecurrenceScope.entries.filter { it != RecurrenceScope.THIS || canMoveOne }
}

object MoveRules {
    /** `CalendarContract.ACCOUNT_TYPE_LOCAL`. */
    const val LOCAL_ACCOUNT_TYPE = "LOCAL"

    fun of(calendar: CalendarInfo) = MoveRule(
        canEdit = calendar.access.canEdit,
        canMoveOne = calendar.account.type != LOCAL_ACCOUNT_TYPE
    )

    /** The rules of each calendar, by id. */
    fun byCalendar(calendars: List<CalendarInfo>): Map<CalendarId, MoveRule> =
        calendars.associate { it.id to of(it) }

    /**
     * Whether [instance] can be picked up. A calendar that is not known (not loaded yet, or gone)
     * cannot be edited as far as anyone can tell.
     */
    fun canPickUp(rules: Map<CalendarId, MoveRule>, instance: EventInstance): Boolean =
        rules[instance.calendarId]?.canEdit == true
}
