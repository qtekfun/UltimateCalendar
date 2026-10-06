// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.data.source.caldav.AccountRemovedListener
import javax.inject.Inject

/**
 * After the CalDAV account left the phone (T37): the reminders are planned again, now without
 * its events, so their alarms are cancelled, and the re-reminders of its invitations (already
 * deleted from the log) lose theirs too.
 */
class RemindersAfterSignOut @Inject constructor(
    private val reminders: ReminderCoordinator,
    private val reRemindings: ReRemindCoordinator
) : AccountRemovedListener {
    override suspend fun accountRemoved() {
        reminders.refresh()
        reRemindings.reconcileStored()
    }
}
