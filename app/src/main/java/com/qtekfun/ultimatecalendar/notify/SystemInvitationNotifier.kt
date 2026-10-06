// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.domain.invitations.ChangeNotifications
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationChanges
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationNotificationPlanner
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationNotifier
import com.qtekfun.ultimatecalendar.domain.invitations.NotificationOp
import javax.inject.Inject
import javax.inject.Singleton

/** Which optional notifications Settings turned on (RF-07), read at the moment of notifying. */
fun interface ChangeNotificationSettings {
    fun current(): ChangeNotifications
}

/**
 * The real [InvitationNotifier] (RF-07): applies what [InvitationNotificationPlanner] decides to
 * the system notifications. Repeating a call replaces notifications instead of duplicating them,
 * as the checker may call it again after the process died.
 */
@Singleton
class SystemInvitationNotifier @Inject constructor(
    private val surface: InvitationNotificationSurface,
    private val settings: ChangeNotificationSettings
) : InvitationNotifier {
    override suspend fun notify(changes: InvitationChanges) {
        val operations = InvitationNotificationPlanner.plan(changes, settings.current())
        if (operations.isEmpty()) return
        operations.forEach(::apply)
        surface.refreshSummary()
    }

    private fun apply(operation: NotificationOp) = when (operation) {
        is NotificationOp.ShowInvitation -> surface.show(operation.invitation, operation.alert)
        is NotificationOp.CancelInvitation -> surface.cancel(operation.key)
        is NotificationOp.ShowMoved -> surface.showMoved(operation.invitation)
        is NotificationOp.ShowCancelled -> surface.showCancelled(operation.invitation)
        is NotificationOp.ClearChanges -> surface.clearChanges(operation.key)
    }
}
