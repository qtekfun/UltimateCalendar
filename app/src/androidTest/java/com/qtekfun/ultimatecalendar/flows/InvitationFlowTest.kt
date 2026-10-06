// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.flows

import android.app.NotificationManager
import android.provider.CalendarContract.Attendees
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.NotificationTags
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.sync.InvitationChecker
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Flows 2 and 3: answering an invitation from the inbox and from its notification. An invitation
 * is an event of a calendar whose owner is an attendee who has not answered.
 */
@HiltAndroidTest
class InvitationFlowTest : FlowTest() {
    @Inject
    lateinit var checker: InvitationChecker

    private fun seedInvitation(title: String): EventId = calendars.seed(
        calendars.timed(
            title,
            calendars.work,
            calendars.tomorrowAt(hour = 15),
            attendees = listOf(Attendee.of(calendars.me))
        )
    )

    @Test
    fun acceptingFromTheInboxStoresTheAnswerInTheProvider() {
        val title = calendars.unique("Planning")
        val id = seedInvitation(title)
        assertEquals(
            "seeded as unanswered",
            Attendees.ATTENDEE_STATUS_INVITED,
            calendars.attendeeStatus(id, calendars.me)
        )

        launchApp()
        clickDescribed("Invitations, 1 pending")
        waitForText(title)
        click("Accept")

        waitUntil {
            calendars.attendeeStatus(id, calendars.me) == Attendees.ATTENDEE_STATUS_ACCEPTED
        }
        assertEquals(
            "the event's own status follows",
            Attendees.ATTENDEE_STATUS_ACCEPTED,
            calendars.eventsTitled(title).single().selfStatus
        )
        waitForText("You accepted $title")
        waitForText("No pending invitations")
    }

    /**
     * The notification is posted by the real checker and notifier; its Accept button is fired
     * through the very `PendingIntent` the notification carries, which reaches the real receiver.
     * Tapping the button in the shade is not driven: that needs UiAutomator (not a dependency
     * here) and the shade's layout and heads-up behaviour differ between API 26 and 36.
     */
    @Test
    fun acceptingFromTheNotificationStoresTheAnswerAndRemovesIt() {
        val title = calendars.unique("Review")
        val id = seedInvitation(title)
        val manager = context.getSystemService(NotificationManager::class.java)
        val tag = NotificationTags.invitation(InvitationKey(calendars.work, id))

        runBlocking { checker.check(requestSync = false) }

        waitUntil { manager.activeNotifications.any { it.tag == tag } }
        val accept = manager.activeNotifications.first { it.tag == tag }
            .notification.actions.orEmpty().first { it.title.toString() == "Accept" }
        accept.actionIntent.send()

        waitUntil {
            calendars.attendeeStatus(id, calendars.me) == Attendees.ATTENDEE_STATUS_ACCEPTED
        }
        waitUntil { manager.activeNotifications.none { it.tag == tag } }
    }
}
