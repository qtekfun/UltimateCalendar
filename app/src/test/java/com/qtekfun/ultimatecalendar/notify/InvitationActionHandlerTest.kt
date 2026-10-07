// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.invitations.ReplyStatus
import com.qtekfun.ultimatecalendar.data.invitations.ResponseOutcome
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAnswer
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.sync.InMemoryNotifiedDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvitationActionHandlerTest {
    private val surface = RecordingSurface()
    private val responses = FixedResponses(ResponseOutcome.Answered)
    private val notified = NotifiedInvitations(InMemoryNotifiedDao(), Dispatchers.Unconfined)
    private var waiting = false
    private var rechecks = 0
    private val handler = InvitationActionHandler(
        responses,
        surface,
        notified,
        ReplyStatus { waiting },
        { rechecks++ }
    )
    private val lunch = sampleInvitation(7, "Lunch")

    @Test
    fun `each button writes its own status for its own invitation`() = runTest {
        for (answer in InvitationAnswer.entries) handler.answer(lunch.key, answer)
        assertEquals(
            listOf(AttendeeStatus.ACCEPTED, AttendeeStatus.TENTATIVE, AttendeeStatus.DECLINED),
            responses.asked.map { it.second }
        )
        assertTrue(responses.asked.all { it.first == lunch.key })
    }

    @Test
    fun `a stored answer removes the notification, says so and looks again`() = runTest {
        handler.answer(lunch.key, InvitationAnswer.ACCEPT)
        assertEquals(listOf("cancel 7", "summary", "answered ACCEPT"), surface.calls)
        assertEquals(1, rechecks)
    }

    @Test
    fun `an answer whose account cannot sync now says the reply goes out later`() = runTest {
        waiting = true
        handler.answer(lunch.key, InvitationAnswer.MAYBE)
        assertEquals(listOf("cancel 7", "summary", "answered MAYBE waiting"), surface.calls)
    }

    @Test
    fun `an invitation whose account has no copy yet stays, saying it waits for it`() = runTest {
        responses.outcome = ResponseOutcome.WaitingForAccount("b@gmail.com")
        notified.replaceAll(listOf(lunch))

        handler.answer(lunch.key, InvitationAnswer.ACCEPT)
        // Nothing is announced as answered, nothing is removed and nothing is looked at again.
        assertEquals(listOf("waiting 7"), surface.calls)
        assertEquals(0, rechecks)
    }

    @Test
    fun `an invitation that is no longer in the record says nothing while it waits`() = runTest {
        responses.outcome = ResponseOutcome.WaitingForAccount("b@gmail.com")

        handler.answer(lunch.key, InvitationAnswer.ACCEPT)

        assertEquals(emptyList<String>(), surface.calls)
    }

    @Test
    fun `pressing the same button twice leaves the same screen`() = runTest {
        handler.answer(lunch.key, InvitationAnswer.ACCEPT)
        handler.answer(lunch.key, InvitationAnswer.ACCEPT)
        assertEquals(
            listOf(
                "cancel 7",
                "summary",
                "answered ACCEPT",
                "cancel 7",
                "summary",
                "answered ACCEPT"
            ),
            surface.calls
        )
    }

    @Test
    fun `an event deleted meanwhile also clears the notification without claiming an answer`() =
        runTest {
            responses.outcome = ResponseOutcome.Gone
            handler.answer(lunch.key, InvitationAnswer.DECLINE)
            assertEquals(listOf("cancel 7", "summary"), surface.calls)
            assertEquals(1, rechecks)
        }

    @Test
    fun `a failed answer keeps the notification and says it was not sent`() = runTest {
        notified.replaceAll(listOf(sampleInvitation(1), lunch))
        responses.outcome = ResponseOutcome.Failed(CalendarError.SourceFailure("down"))
        handler.answer(lunch.key, InvitationAnswer.MAYBE)
        assertEquals(listOf("failed 7"), surface.calls)
        assertEquals(0, rechecks)
    }

    @Test
    fun `a failed answer for an invitation that was never recorded touches nothing`() = runTest {
        responses.outcome = ResponseOutcome.Failed(CalendarError.PermissionDenied)
        handler.answer(lunch.key, InvitationAnswer.MAYBE)
        assertTrue(surface.calls.isEmpty())
    }
}
