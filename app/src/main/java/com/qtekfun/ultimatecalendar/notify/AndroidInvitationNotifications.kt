// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAlert
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAnswer
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationNotificationPlanner
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationTimeText
import com.qtekfun.ultimatecalendar.domain.invitations.NotificationTags
import com.qtekfun.ultimatecalendar.domain.invitations.SummaryOp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.Objects
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The invitation notifications on the real notification manager (RF-07). Each invitation has its
 * own notification, identified by a tag made of its ids, so showing it again replaces it. They
 * share a group, with a summary while two or more are on screen. Without the notification
 * permission nothing is shown and nothing fails; the tray keeps working. Titles, places and
 * addresses go only into the notification, never into logs.
 */
@Singleton
class AndroidInvitationNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zone: SystemZone
) : InvitationNotificationSurface {
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    override fun show(invitation: Invitation, alert: InvitationAlert) {
        val tag = NotificationTags.invitation(invitation.key)
        val silent = when (alert) {
            InvitationAlert.NEW -> isShown(tag)
            InvitationAlert.CHANGED -> false
            InvitationAlert.SILENT -> true
        }
        post(tag, invitationBuilder(invitation, silent, failed = false))
    }

    override fun showAnswerFailed(invitation: Invitation) {
        val tag = NotificationTags.invitation(invitation.key)
        post(tag, invitationBuilder(invitation, silent = true, failed = true))
    }

    override fun showMoved(invitation: Invitation) = info(
        NotificationTags.moved(invitation.key),
        invitation,
        R.string.invitation_moved
    )

    override fun showCancelled(invitation: Invitation) = info(
        NotificationTags.cancelled(invitation.key),
        invitation,
        R.string.invitation_cancelled
    )

    override fun cancel(key: InvitationKey) = manager.cancel(NotificationTags.invitation(key), ID)

    override fun refreshSummary() {
        val shown = manager.activeNotifications.count { NotificationTags.isInvitation(it.tag) }
        when (val op = InvitationNotificationPlanner.summary(shown)) {
            is SummaryOp.Show -> post(NotificationTags.SUMMARY, summaryBuilder(op.count))
            SummaryOp.Cancel -> manager.cancel(NotificationTags.SUMMARY, ID)
        }
    }

    private fun invitationBuilder(
        invitation: Invitation,
        silent: Boolean,
        failed: Boolean
    ): NotificationCompat.Builder {
        val details = listOfNotNull(
            timeText(invitation),
            invitation.location?.takeIf { it.isNotBlank() },
            context.getString(R.string.invitation_answer_failed).takeIf { failed }
        )
        val builder = base(NotificationChannels.INVITATIONS)
            .setContentTitle(invitation.title)
            .setContentText(details.first())
            .setSubText(invitation.organizer)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details.joinToString("\n")))
            .setContentIntent(open(NotificationRoute.Event(invitation.key)))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(silent)
        InvitationAnswer.entries.forEach { builder.addAction(button(invitation.key, it)) }
        return builder
    }

    private fun summaryBuilder(count: Int): NotificationCompat.Builder =
        base(NotificationChannels.INVITATIONS)
            .setContentTitle(
                context.resources.getQuantityString(R.plurals.invitation_summary, count, count)
            )
            .setContentText(context.getString(R.string.invitation_summary_text))
            .setContentIntent(open(NotificationRoute.Inbox))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)

    private fun info(tag: String, invitation: Invitation, @StringRes headline: Int) {
        val builder = base(NotificationChannels.CHANGES)
            .setContentTitle(invitation.title)
            .setContentText(context.getString(headline, timeText(invitation)))
            .setContentIntent(open(NotificationRoute.Inbox))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        post(tag, builder)
    }

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setAutoCancel(true)

    private fun button(key: InvitationKey, answer: InvitationAnswer): NotificationCompat.Action {
        val label = when (answer) {
            InvitationAnswer.ACCEPT -> R.string.invitation_accept
            InvitationAnswer.MAYBE -> R.string.invitation_maybe
            InvitationAnswer.DECLINE -> R.string.invitation_decline
        }
        val intent = PendingIntent.getBroadcast(
            context,
            Objects.hash(NotificationTags.invitation(key), answer.name),
            InvitationIntents.answer(context, key, answer),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, context.getString(label), intent).build()
    }

    private fun open(route: NotificationRoute): PendingIntent = PendingIntent.getActivity(
        context,
        Objects.hash(route),
        InvitationIntents.open(context, route),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun timeText(invitation: Invitation) = InvitationTimeText.format(
        invitation.time,
        zone.current(),
        Locale.getDefault()
    )

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
        const val GROUP = "invitations"
        const val ID = 0
    }
}
