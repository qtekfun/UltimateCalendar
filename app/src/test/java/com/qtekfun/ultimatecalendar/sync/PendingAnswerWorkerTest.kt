// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.qtekfun.ultimatecalendar.data.invitations.AnswerAttempt
import com.qtekfun.ultimatecalendar.data.invitations.ForeignAnswers
import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.notify.InvitationNotificationSurface
import com.qtekfun.ultimatecalendar.notify.InvitationRecheck
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PendingAnswerWorkerTest {
    private val key = InvitationKey(CalendarId(1), EventId(7), "b@gmail.com")
    private val invitation = Invitation(
        key,
        "Dinner",
        EventTime.Timed(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), ZoneOffset.UTC),
        account = "b@gmail.com"
    )
    private val answers = mockk<ForeignAnswers>()
    private val surface = mockk<InvitationNotificationSurface>(relaxed = true)
    private val notified = mockk<NotifiedInvitations>()
    private var rechecks = 0
    private var attempt = 0
    private var input: Data = PendingAnswerWorker.inputOf(key, AttendeeStatus.ACCEPTED)
    private val params = mockk<WorkerParameters> {
        every { runAttemptCount } answers { attempt }
        every { inputData } answers { input }
    }
    private val worker = PendingAnswerWorker(
        mockk(relaxed = true),
        params,
        answers,
        surface,
        notified,
        InvitationRecheck { rechecks++ }
    )

    init {
        coEvery { notified.load() } returns listOf(invitation)
    }

    @Test
    fun `when the copy arrives the answer is given, the notification goes and a check runs`() =
        runTest {
            coEvery { answers.attempt(key, AttendeeStatus.ACCEPTED, false) } returns
                AnswerAttempt.ANSWERED

            assertEquals(ListenableWorker.Result.success(), worker.doWork())

            verify { surface.cancel(key) }
            verify { surface.refreshSummary() }
            assertEquals(1, rechecks)
        }

    @Test
    fun `an event that is gone is done the same way`() = runTest {
        coEvery { answers.attempt(key, AttendeeStatus.ACCEPTED, false) } returns AnswerAttempt.GONE

        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        verify { surface.cancel(key) }
        assertEquals(1, rechecks)
    }

    @Test
    fun `while the copy has not arrived the work is retried and says nothing`() = runTest {
        coEvery { answers.attempt(key, AttendeeStatus.ACCEPTED, false) } returns AnswerAttempt.RETRY

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())

        verify(exactly = 0) { surface.showNeverArrived(any()) }
        assertEquals(0, rechecks)
    }

    @Test
    fun `the last try knows it is the last and, when it gives up, says so honestly`() = runTest {
        attempt = PendingAnswerWorker.MAX_ATTEMPTS - 1
        coEvery { answers.attempt(key, AttendeeStatus.ACCEPTED, true) } returns
            AnswerAttempt.GAVE_UP

        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        verify { surface.showNeverArrived(invitation) }
        verify(exactly = 0) { surface.cancel(any()) }
    }

    @Test
    fun `giving up on an invitation that is no longer recorded shows nothing`() = runTest {
        coEvery { notified.load() } returns emptyList()
        coEvery { answers.attempt(key, AttendeeStatus.ACCEPTED, false) } returns
            AnswerAttempt.GAVE_UP

        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        verify(exactly = 0) { surface.showNeverArrived(any()) }
    }

    @Test
    fun `work without a well-formed input fails and touches nothing`() = runTest {
        val missing = Data.Builder().putLong("calendar", 1).putLong("event", 7).build()
        val wrongStatus = Data.Builder()
            .putLong("calendar", 1)
            .putLong("event", 7)
            .putString("address", "b@gmail.com")
            .putString("status", "NOPE")
            .build()
        val noAddress = Data.Builder()
            .putLong("calendar", 1)
            .putLong("event", 7)
            .putString("status", AttendeeStatus.ACCEPTED.name)
            .build()
        val noEvent = Data.Builder().putString("address", "b@gmail.com").build()

        for (bad in listOf(missing, wrongStatus, noAddress, noEvent, Data.EMPTY)) {
            input = bad
            assertEquals(ListenableWorker.Result.failure(), worker.doWork())
        }

        coVerify(exactly = 0) { answers.attempt(any(), any(), any()) }
    }

    @Test
    fun `each invitation has its own unique work, without the address in its name`() {
        val other = key.copy(address = "c@gmail.com")

        assertNotEquals(PendingAnswerWorker.uniqueName(key), PendingAnswerWorker.uniqueName(other))
        assertEquals(false, PendingAnswerWorker.uniqueName(key).contains("gmail"))
    }
}
