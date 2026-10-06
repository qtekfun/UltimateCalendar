// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAnswer
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.ui.MainActivity
import java.time.LocalDate

/** Where tapping a notification (or a home-screen widget, T38) of the app takes the user. */
sealed interface NotificationRoute {
    /** The invitation tray (the group summary). */
    data object Inbox : NotificationRoute

    /** The detail of one occurrence (one invitation). */
    data class Event(val ref: EventRef) : NotificationRoute

    /** The Day view on [date]: a tapped day of the Month widget. */
    data class Day(val date: LocalDate) : NotificationRoute

    /** The editor on a new event: the "+" of the Agenda widget. */
    data object NewEvent : NotificationRoute
}

/**
 * The intents behind the notifications of invitations: opening the app on a route, and the three
 * answer buttons. Only database ids travel in them, never titles or addresses.
 */
object InvitationIntents {
    const val ACTION_ANSWER = "com.qtekfun.ultimatecalendar.action.ANSWER_INVITATION"
    private const val ACTION_OPEN = "com.qtekfun.ultimatecalendar.action.OPEN_INVITATION"
    private const val EXTRA_CALENDAR = "invitation_calendar"
    private const val EXTRA_EVENT = "invitation_event"
    private const val EXTRA_ANSWER = "invitation_answer"
    private const val EXTRA_INBOX = "invitation_inbox"
    private const val EXTRA_REF = "invitation_ref"
    private const val MISSING = -1L

    fun open(context: Context, route: NotificationRoute): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .also { intent ->
                when (route) {
                    NotificationRoute.Inbox -> intent.putExtra(EXTRA_INBOX, true)

                    is NotificationRoute.Event -> intent.putExtra(EXTRA_REF, route.ref.encode())

                    // Only the widgets ask for these, with intents of their own (WidgetIntents).
                    is NotificationRoute.Day, NotificationRoute.NewEvent -> Unit
                }
            }

    /** The route an intent that started the app asks for, or null for an ordinary start. */
    fun routeOf(intent: Intent): NotificationRoute? = when {
        intent.action != ACTION_OPEN -> null
        intent.getBooleanExtra(EXTRA_INBOX, false) -> NotificationRoute.Inbox
        else -> EventRef.decode(intent.getStringExtra(EXTRA_REF))?.let(NotificationRoute::Event)
    }

    fun answer(context: Context, key: InvitationKey, answer: InvitationAnswer): Intent =
        Intent(context, InvitationActionReceiver::class.java)
            .setAction(ACTION_ANSWER)
            .putExtra(EXTRA_ANSWER, answer.name)
            .also { putKey(it, key) }

    /** The invitation and answer of a button, or null if the intent is not a well-formed one. */
    fun answerOf(intent: Intent): Pair<InvitationKey, InvitationAnswer>? {
        val key = keyOf(intent)
        val answer = InvitationAnswer.entries
            .firstOrNull { it.name == intent.getStringExtra(EXTRA_ANSWER) }
        return if (key == null || answer == null) null else key to answer
    }

    private fun putKey(intent: Intent, key: InvitationKey) {
        intent.putExtra(EXTRA_CALENDAR, key.calendarId.value)
        intent.putExtra(EXTRA_EVENT, key.eventId.value)
    }

    private fun keyOf(intent: Intent): InvitationKey? {
        val calendar = intent.getLongExtra(EXTRA_CALENDAR, MISSING)
        val event = intent.getLongExtra(EXTRA_EVENT, MISSING)
        return if (calendar == MISSING || event == MISSING) {
            null
        } else {
            InvitationKey(CalendarId(calendar), EventId(event))
        }
    }
}
