// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar

import android.content.Context
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionRepository
import com.qtekfun.ultimatecalendar.notify.KeepAliveController
import com.qtekfun.ultimatecalendar.notify.NotificationChannels
import com.qtekfun.ultimatecalendar.notify.ReRemindCoordinator
import com.qtekfun.ultimatecalendar.notify.ReminderCoordinator
import com.qtekfun.ultimatecalendar.sync.CalDavSync
import com.qtekfun.ultimatecalendar.sync.InvitationCheckCoordinator
import com.qtekfun.ultimatecalendar.widget.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Everything that has to be running for as long as the app's process lives, in one place: the
 * alarms for reminders, the periodic invitation check, the syncs and the widgets (RF-06 to
 * RF-12). `UltimateCalendarApp` calls [start] when the process starts; the instrumented tests
 * call the same function (their application is a Hilt test one), so a coordinator forgotten
 * here is a test that fails.
 */
@Singleton
@Suppress("LongParameterList")
class AppStartup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminders: ReminderCoordinator,
    private val reRemindings: ReRemindCoordinator,
    private val keepAlive: KeepAliveController,
    private val invitationChecks: InvitationCheckCoordinator,
    private val widgets: WidgetRefresher,
    private val caldavSync: CalDavSync,
    private val subscriptions: SubscriptionRepository
) {
    /** Starts what follows the calendar, the settings and the account until [scope] ends. */
    fun start(scope: CoroutineScope) {
        NotificationChannels.ensureCreated(context)
        // Alarms, recovery of missed reminders and robust mode live as long as the process.
        reminders.start(scope)
        keepAlive.start(scope)
        // The extra reminders of unanswered invitations (T40).
        reRemindings.start(scope)
        // The periodic invitation check, and the checks the provider's changes and the app
        // opening start (RF-06).
        invitationChecks.start(scope)
        // CalDAV syncs exist only while an account is signed in (RF-12).
        caldavSync.start(scope)
        // The periodic refresh of the subscriptions exists only while one needs it (T39).
        scope.launch { subscriptions.reschedule() }
        // The home-screen widgets follow the calendar while the process lives (T38).
        widgets.start(scope)
    }

    /** An activity of the app came to the foreground. */
    fun onActivityStarted(scope: CoroutineScope) {
        // Calendar access may have just been granted: plan the reminders again.
        reminders.refresh()
        invitationChecks.onAppOpened()
        caldavSync.onAppOpened()
        scope.launch { widgets.refreshAll() }
    }
}
