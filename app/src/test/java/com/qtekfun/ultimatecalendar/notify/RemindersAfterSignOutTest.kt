// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class RemindersAfterSignOutTest {
    private val reminders = mockk<ReminderCoordinator> { every { refresh() } returns Unit }
    private val reRemindings = mockk<ReRemindCoordinator> {
        coEvery { reconcileStored() } returns Unit
    }

    @Test
    fun `the reminders are planned again, then the re-reminders follow what is left`() =
        runBlocking {
            RemindersAfterSignOut(reminders, reRemindings).accountRemoved()

            verify(exactly = 1) { reminders.refresh() }
            coVerifyOrder {
                reminders.refresh()
                reRemindings.reconcileStored()
            }
        }
}
