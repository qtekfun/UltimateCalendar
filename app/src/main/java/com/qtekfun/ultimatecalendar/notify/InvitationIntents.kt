// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
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

    /** The CalDAV connection screen: the first-run wizard's "Connect a CalDAV server" (T37). */
    data object ConnectCalDav : NotificationRoute
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
    private const val EXTRA_ADDRESS = "invitation_address"
    private const val EXTRA_ANSWER = "invitation_answer"
    private const val EXTRA_INBOX = "invitation_inbox"
    private const val EXTRA_REF = "invitation_ref"
    private const val MISSING = -1L
    private const val WEB_CALENDAR = "https://calendar.google.com/calendar/r"

    fun open(context: Context, route: NotificationRoute): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .also { intent ->
                when (route) {
                    NotificationRoute.Inbox -> intent.putExtra(EXTRA_INBOX, true)

                    is NotificationRoute.Event -> intent.putExtra(EXTRA_REF, route.ref.encode())

                    // Only the widgets and the wizard ask for these, never through an intent.
                    is NotificationRoute.Day,
                    NotificationRoute.NewEvent,
                    NotificationRoute.ConnectCalDav -> Unit
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

    /**
     * Opens the web calendar of [account] in the browser (or whatever handles the link), where an
     * invitation that the account's calendar on the phone never received can be answered.
     */
    fun viewCalendar(account: String): Intent = Intent(
        Intent.ACTION_VIEW,
        WEB_CALENDAR.toUri().buildUpon().appendQueryParameter("authuser", account).build()
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun putKey(intent: Intent, key: InvitationKey) {
        intent.putExtra(EXTRA_CALENDAR, key.calendarId.value)
        intent.putExtra(EXTRA_EVENT, key.eventId.value)
        // The address of another of the user's accounts, kept inside the app's own intent.
        intent.putExtra(EXTRA_ADDRESS, key.address)
    }

    private fun keyOf(intent: Intent): InvitationKey? {
        val calendar = intent.getLongExtra(EXTRA_CALENDAR, MISSING)
        val event = intent.getLongExtra(EXTRA_EVENT, MISSING)
        return if (calendar == MISSING || event == MISSING) {
            null
        } else {
            InvitationKey(
                CalendarId(calendar),
                EventId(event),
                intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
            )
        }
    }
}
