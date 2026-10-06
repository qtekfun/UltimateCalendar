// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import androidx.work.ListenableWorker
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationChanges
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.notify.MissedReminderRecovery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InvitationCheckWorkerTest {
    private val checker = mockk<InvitationChecker>()
    private val recovery = mockk<MissedReminderRecovery>(relaxed = true)
    private val worker =
        InvitationCheckWorker(mockk(relaxed = true), mockk(relaxed = true), checker, recovery)
    private val nothing = InvitationChanges(emptyList(), emptyList(), emptyList(), emptyList())

    @Test
    fun `a finished check succeeds and then recovers missed reminders`() = runTest {
        coEvery { checker.check(true) } returns InvitationCheckOutcome.Done(nothing, 0)

        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        coVerify(exactly = 1) { checker.check(true) }
        coVerify(exactly = 1) { recovery.recover() }
    }

    @Test
    fun `a failing source is retried soon, and reminders are still recovered`() = runTest {
        coEvery { checker.check(true) } returns
            InvitationCheckOutcome.Failed(CalendarError.SourceFailure("down"))

        assertInstanceOf(ListenableWorker.Result.Retry::class.java, worker.doWork())

        coVerify(exactly = 1) { recovery.recover() }
    }

    @Test
    fun `a revoked permission is not retried, the next period tries again`() = runTest {
        coEvery { checker.check(true) } returns
            InvitationCheckOutcome.Failed(CalendarError.PermissionDenied)

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test
    fun `the factory builds this worker and leaves any other to WorkManager`() {
        val factory = InvitationWorkerFactory(Provider { checker }, Provider { recovery })

        val built = factory.createWorker(
            mockk(relaxed = true),
            InvitationCheckWorker::class.java.name,
            mockk(relaxed = true)
        )
        val other = factory.createWorker(
            mockk(relaxed = true),
            "com.example.OtherWorker",
            mockk(relaxed = true)
        )

        assertInstanceOf(InvitationCheckWorker::class.java, built)
        assertNull(other)
    }
}
