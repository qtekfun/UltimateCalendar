// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
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
    private val handler = InvitationActionHandler(responses, surface, notified)
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
    fun `a stored answer removes the notification and refreshes the group`() = runTest {
        handler.answer(lunch.key, InvitationAnswer.ACCEPT)
        assertEquals(listOf("cancel 7", "summary"), surface.calls)
    }

    @Test
    fun `pressing the same button twice leaves the same screen`() = runTest {
        handler.answer(lunch.key, InvitationAnswer.ACCEPT)
        handler.answer(lunch.key, InvitationAnswer.ACCEPT)
        assertEquals(listOf("cancel 7", "summary", "cancel 7", "summary"), surface.calls)
    }

    @Test
    fun `an event deleted meanwhile also clears the notification`() = runTest {
        responses.outcome = ResponseOutcome.Gone
        handler.answer(lunch.key, InvitationAnswer.DECLINE)
        assertEquals(listOf("cancel 7", "summary"), surface.calls)
    }

    @Test
    fun `a failed answer keeps the notification and says it was not sent`() = runTest {
        notified.replaceAll(listOf(sampleInvitation(1), lunch))
        responses.outcome = ResponseOutcome.Failed(CalendarError.SourceFailure("down"))
        handler.answer(lunch.key, InvitationAnswer.MAYBE)
        assertEquals(listOf("failed 7"), surface.calls)
    }

    @Test
    fun `a failed answer for an invitation that was never recorded touches nothing`() = runTest {
        responses.outcome = ResponseOutcome.Failed(CalendarError.PermissionDenied)
        handler.answer(lunch.key, InvitationAnswer.MAYBE)
        assertTrue(surface.calls.isEmpty())
    }
}
