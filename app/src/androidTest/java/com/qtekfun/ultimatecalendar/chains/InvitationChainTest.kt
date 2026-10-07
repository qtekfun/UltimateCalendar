// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.chains

import android.provider.CalendarContract.Attendees
import android.util.Log
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.NotificationTags
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RF-06 and RF-07 from the provider to the system, with the app's start-up running: an invitation
 * that appears in the provider is noticed by the provider's change (no check is called by hand),
 * the notification is posted, the inbox lists it, and Accept on the notification reaches the
 * provider. Answering from the inbox is in the flow tests.
 */
@HiltAndroidTest
class InvitationChainTest : ChainTest() {
    @Test
    fun anInvitationInTheProviderIsNotifiedListedAndAnsweredFromTheNotification() {
        startProcess()
        Log.i("UC-DIAG", "test: process started")
        val title = calendars.unique("Offsite")
        val id = calendars.seed(
            calendars.timed(
                title,
                calendars.work,
                calendars.tomorrowAt(hour = 11),
                attendees = listOf(Attendee.of(calendars.me))
            )
        )
        Log.i("UC-DIAG", "test: seeded")
        val tag = NotificationTags.invitation(InvitationKey(calendars.work, id))

        // The provider's change starts a check after a short debounce; nobody calls the checker.
        waitUntil(INVITATION_TIMEOUT_MS) { notifications.activeNotifications.any { it.tag == tag } }

        Log.i("UC-DIAG", "test: notified")
        launchApp()
        clickDescribed("Invitations, 1 pending")
        waitForText(title)
        closeApp()

        val before = calendars.syncState(id)
        val accept = notifications.activeNotifications.first { it.tag == tag }
            .notification.actions.orEmpty().first { it.title.toString() == "Accept" }
        accept.actionIntent.send()
        waitUntil {
            calendars.attendeeStatus(id, calendars.me) == Attendees.ATTENDEE_STATUS_ACCEPTED
        }
        waitUntil { notifications.activeNotifications.none { it.tag == tag } }
        assertEquals(
            Attendees.ATTENDEE_STATUS_ACCEPTED,
            calendars.eventsTitled(title).single().selfStatus
        )
        // What Google's adapter uploads from: the provider marks the event dirty for any client
        // write to its attendees (the emulator's LOCAL account shows it too), and the app never
        // touches the sync id.
        val after = calendars.syncState(id)
        assertEquals("the answer marks the event for upload", 1, after.first)
        assertEquals("the sync id is untouched", before.second, after.second)
    }

    private companion object {
        const val INVITATION_TIMEOUT_MS = 90_000L
    }
}
