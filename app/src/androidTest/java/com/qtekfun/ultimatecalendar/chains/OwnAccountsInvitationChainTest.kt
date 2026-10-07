// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.chains

import android.app.Notification
import android.provider.CalendarContract.Attendees
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.NotificationTags
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.EventId
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RF-06 and RF-07 with two accounts of the user on the phone (two calendars with different owner
 * addresses): an event of one account that invites the other is an invitation for the other, shown
 * from the first account's event while the other has no copy, answered only in the other account's
 * own copy once it exists, and never written into the organizer's event.
 */
@HiltAndroidTest
class OwnAccountsInvitationChainTest : ChainTest() {
    private val other = "second.account@example.org"

    private fun invite(title: String): EventId {
        val organised = calendars.seed(
            calendars.timed(
                title,
                calendars.work,
                calendars.tomorrowAt(hour = 15),
                attendees = listOf(
                    Attendee.of(calendars.me, status = AttendeeStatus.ACCEPTED, isOrganizer = true),
                    Attendee.of(this.other)
                )
            )
        )
        calendars.setUid(organised, UID)
        return organised
    }

    private fun notification(tag: String) =
        notifications.activeNotifications.firstOrNull { it.tag == tag }

    @Test
    fun anInvitationToMySecondAccountIsFoundAnsweredInItsOwnCopyAndNeverInTheOrganizers() {
        val second = calendars.createOtherAccount(other)
        startProcess()
        val title = calendars.unique("Family dinner")
        val organised = invite(title)
        val ownTag = NotificationTags.invitation(InvitationKey(calendars.work, organised))
        val foreignTag = NotificationTags.invitation(
            InvitationKey(calendars.work, organised, other)
        )

        // The organizer's event is the only copy: the invitation is for the second account.
        waitUntil(INVITATION_TIMEOUT_MS) { notification(foreignTag) != null }
        assertTrue("the organizer's own row is no invitation", notification(ownTag) == null)

        // The second account's sync delivers its own copy of the event.
        val copy = calendars.seed(
            calendars.timed(
                title,
                second,
                calendars.tomorrowAt(hour = 15),
                attendees = listOf(
                    Attendee.of(calendars.me, status = AttendeeStatus.ACCEPTED, isOrganizer = true),
                    Attendee.of(other)
                )
            )
        )
        calendars.setUid(copy, UID)
        calendars.organizeBy(copy, calendars.me)
        val copyTag = NotificationTags.invitation(InvitationKey(second, copy))
        waitUntil(INVITATION_TIMEOUT_MS) { notification(copyTag) != null }
        waitUntil(INVITATION_TIMEOUT_MS) { notification(foreignTag) == null }

        val accept = checkNotNull(notification(copyTag)).notification.actions.orEmpty()
            .first { it.title.toString() == "Accept" }
        accept.actionIntent.send()
        waitUntil {
            calendars.attendeeStatus(copy, other) == Attendees.ATTENDEE_STATUS_ACCEPTED
        }
        waitUntil { notification(copyTag) == null }

        assertEquals(
            "the organizer's event is never answered for the second account",
            Attendees.ATTENDEE_STATUS_INVITED,
            calendars.attendeeStatus(organised, other)
        )
    }

    @Test
    fun answeringBeforeTheCopyArrivesWritesNothingAndSaysItIsWaiting() {
        calendars.createOtherAccount(other)
        startProcess()
        val title = calendars.unique("Brunch")
        val organised = invite(title)
        val foreignTag = NotificationTags.invitation(
            InvitationKey(calendars.work, organised, other)
        )
        waitUntil(INVITATION_TIMEOUT_MS) { notification(foreignTag) != null }

        checkNotNull(notification(foreignTag)).notification.actions.orEmpty()
            .first { it.title.toString() == "Accept" }.actionIntent.send()

        waitUntil {
            notification(foreignTag)?.notification?.extras
                ?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?.contains("Waiting for the account") == true
        }
        assertEquals(
            "nothing is written to the organizer's event",
            Attendees.ATTENDEE_STATUS_INVITED,
            calendars.attendeeStatus(organised, other)
        )
    }

    private companion object {
        const val UID = "own-accounts-chain@example.org"
        const val INVITATION_TIMEOUT_MS = 90_000L
    }
}
