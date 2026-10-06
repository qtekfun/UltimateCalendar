// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.app.PendingIntent
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAnswer
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationTimeText
import com.qtekfun.ultimatecalendar.domain.invitations.NotificationTags
import com.qtekfun.ultimatecalendar.domain.invitations.detailRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.Objects
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The look of each invitation notification (RF-07): texts, buttons and where a tap goes. Titles,
 * places and addresses go only into the notification, never into logs.
 */
@Singleton
class InvitationNotificationBuilders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zone: SystemZone
) {
    /** The notification that asks for an answer: three buttons, a tap opens the detail. */
    fun invitation(
        invitation: Invitation,
        silent: Boolean,
        failed: Boolean,
        reminder: Boolean = false
    ): NotificationCompat.Builder {
        val details = listOfNotNull(
            timeText(invitation),
            invitation.location?.takeIf { it.isNotBlank() },
            context.getString(R.string.invitation_answer_failed).takeIf { failed },
            context.getString(R.string.invitation_still_waiting).takeIf { reminder }
        )
        val builder = base(NotificationChannels.INVITATIONS)
            .setContentTitle(invitation.title)
            .setContentText(details.first())
            .setSubText(invitation.organizer)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details.joinToString("\n")))
            .setContentIntent(open(NotificationRoute.Event(invitation.detailRef())))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(silent)
        InvitationAnswer.entries.forEach { builder.addAction(button(invitation.key, it)) }
        return builder
    }

    /** The line that gathers two or more invitations; a tap opens the tray. */
    fun summary(count: Int): NotificationCompat.Builder = base(NotificationChannels.INVITATIONS)
        .setContentTitle(
            context.resources.getQuantityString(R.plurals.invitation_summary, count, count)
        )
        .setContentText(context.getString(R.string.invitation_summary_text))
        .setContentIntent(open(NotificationRoute.Inbox))
        .setGroup(GROUP)
        .setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .setOnlyAlertOnce(true)

    /**
     * A note on the changes channel: the organizer moved or cancelled the event. A tap goes to
     * [route]: the event's detail for a move; a cancelled event has no detail to open.
     */
    fun info(
        invitation: Invitation,
        @StringRes headline: Int,
        route: NotificationRoute
    ): NotificationCompat.Builder = base(NotificationChannels.CHANGES)
        .setContentTitle(invitation.title)
        .setContentText(context.getString(headline, timeText(invitation)))
        .setContentIntent(open(route))
        .setCategory(NotificationCompat.CATEGORY_EVENT)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

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

    private companion object {
        const val GROUP = "invitations"
    }
}
