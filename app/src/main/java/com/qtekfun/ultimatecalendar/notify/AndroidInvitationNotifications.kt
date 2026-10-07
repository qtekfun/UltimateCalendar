// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAlert
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAnswer
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationNotificationPlanner
import com.qtekfun.ultimatecalendar.domain.invitations.NotificationTags
import com.qtekfun.ultimatecalendar.domain.invitations.SummaryOp
import com.qtekfun.ultimatecalendar.domain.invitations.detailRef
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The invitation notifications on the real notification manager (RF-07). Each invitation has its
 * own notification, identified by a tag made of its ids, so showing it again replaces it. They
 * share a group, with a summary while two or more are on screen. Without the notification
 * permission nothing is shown and nothing fails; the tray keeps working.
 */
@Singleton
@Suppress("TooManyFunctions")
class AndroidInvitationNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val builders: InvitationNotificationBuilders
) : InvitationNotificationSurface {
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    override fun show(invitation: Invitation, alert: InvitationAlert) {
        val tag = NotificationTags.invitation(invitation.key)
        val silent = when (alert) {
            InvitationAlert.NEW -> isShown(tag)
            InvitationAlert.CHANGED, InvitationAlert.REMINDER -> false
            InvitationAlert.SILENT -> true
        }
        val reminder = alert == InvitationAlert.REMINDER
        post(tag, builders.invitation(invitation, silent, failed = false, reminder = reminder))
    }

    override fun showAnswerFailed(invitation: Invitation) = post(
        NotificationTags.invitation(invitation.key),
        builders.invitation(invitation, silent = true, failed = true)
    )

    override fun showWaitingForAccount(invitation: Invitation) = post(
        NotificationTags.invitation(invitation.key),
        builders.invitation(invitation, silent = true, failed = false, waiting = true)
    )

    override fun showNeverArrived(invitation: Invitation) =
        post(NotificationTags.invitation(invitation.key), builders.undelivered(invitation))

    override fun showAnswered(answer: InvitationAnswer, waiting: Boolean) {
        val done = context.getString(
            when (answer) {
                InvitationAnswer.ACCEPT -> R.string.invitation_sent_accept
                InvitationAnswer.MAYBE -> R.string.invitation_sent_maybe
                InvitationAnswer.DECLINE -> R.string.invitation_sent_decline
            }
        )
        val text = if (waiting) {
            context.getString(R.string.invitation_sent_waiting, done)
        } else {
            done
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    override fun showMoved(invitation: Invitation) = post(
        NotificationTags.moved(invitation.key),
        builders.info(
            invitation,
            R.string.invitation_moved,
            NotificationRoute.Event(invitation.detailRef())
        )
    )

    override fun showCancelled(invitation: Invitation) = post(
        NotificationTags.cancelled(invitation.key),
        builders.info(invitation, R.string.invitation_cancelled, NotificationRoute.Inbox)
    )

    override fun clearChanges(key: InvitationKey) {
        manager.cancel(NotificationTags.moved(key), ID)
        manager.cancel(NotificationTags.cancelled(key), ID)
    }

    override fun cancel(key: InvitationKey) = manager.cancel(NotificationTags.invitation(key), ID)

    override fun refreshSummary() {
        val shown = manager.activeNotifications.count { NotificationTags.isInvitation(it.tag) }
        when (val op = InvitationNotificationPlanner.summary(shown)) {
            is SummaryOp.Show -> post(NotificationTags.SUMMARY, builders.summary(op.count))
            SummaryOp.Cancel -> manager.cancel(NotificationTags.SUMMARY, ID)
        }
    }

    private fun isShown(tag: String) = manager.activeNotifications.any { it.tag == tag }

    private fun post(tag: String, builder: NotificationCompat.Builder) {
        if (!canNotify()) return
        NotificationChannels.ensureCreated(context)
        try {
            NotificationManagerCompat.from(context).notify(tag, ID, builder.build())
        } catch (_: SecurityException) {
            // The permission was revoked between the check and now: nothing to show.
        }
    }

    // Before Android 13 notifications need no permission.
    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private companion object {
        const val ID = 0
    }
}
