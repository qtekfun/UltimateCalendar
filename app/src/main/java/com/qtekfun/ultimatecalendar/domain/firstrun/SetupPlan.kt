// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

import com.qtekfun.ultimatecalendar.domain.firstrun.SetupItem.State

/** Which steps the wizard shows for the current [SetupStatus], in order (RF-01). */
object SetupPlan {
    fun of(status: SetupStatus): List<SetupItem> = buildList {
        add(SetupItem(SetupStep.CALENDAR_PERMISSION, checked(status.calendarPermission)))
        // Only once the permission lets the app look, and only if there is really nothing.
        if (status.calendarPermission && status.hasCalendars == false) {
            add(SetupItem(SetupStep.ADD_ACCOUNT, State.TODO))
        }
        add(SetupItem(SetupStep.NOTIFICATIONS, checked(status.notifications)))
        add(SetupItem(SetupStep.EXACT_ALARMS, checked(status.exactAlarms)))
        add(SetupItem(SetupStep.BATTERY, checked(status.batteryExempt)))
        add(SetupItem(SetupStep.AUTOSTART, State.ADVICE))
        if (status.otherCalendarApps.isNotEmpty()) {
            add(SetupItem(SetupStep.OTHER_CALENDAR_APPS, State.ADVICE))
        }
        add(SetupItem(SetupStep.TEST_REMINDER, State.ADVICE))
    }

    /** The steps the user still has to do, which the app can check. */
    fun pending(status: SetupStatus): List<SetupStep> =
        of(status).filter { it.state == State.TODO }.map { it.step }

    private fun checked(allowed: Boolean) = if (allowed) State.DONE else State.TODO
}
