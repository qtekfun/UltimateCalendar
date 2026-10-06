// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.sync.InvitationCheckOutcome
import com.qtekfun.ultimatecalendar.sync.InvitationChecker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What an alarm of a re-reminder does (T40): looks at the invitations again, so one answered,
 * cancelled or moved since the alarm was set shows nothing, and the check shows what is due. If
 * the calendar cannot be read the invitations of the last check stand in, unless the permission
 * was revoked.
 */
@Singleton
class InvitationReReminderAlarmHandler @Inject constructor(
    private val checker: InvitationChecker,
    private val reReminders: ReRemindCoordinator
) {
    suspend fun onAlarm() {
        val outcome = checker.check(requestSync = false)
        val unreadable = (outcome as? InvitationCheckOutcome.Failed)?.error
        if (unreadable != null && unreadable != CalendarError.PermissionDenied) {
            reReminders.reconcileStored()
        }
    }
}

/** An alarm of a re-reminder went off (T40). */
@AndroidEntryPoint
class InvitationReReminderReceiver : BroadcastReceiver() {
    @Inject
    lateinit var handler: InvitationReReminderAlarmHandler

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                handler.onAlarm()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
