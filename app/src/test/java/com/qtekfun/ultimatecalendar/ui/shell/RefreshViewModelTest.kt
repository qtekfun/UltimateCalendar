// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.sync.InvitationSyncs
import com.qtekfun.ultimatecalendar.domain.refresh.RefreshIssue
import com.qtekfun.ultimatecalendar.domain.refresh.RefreshReport
import com.qtekfun.ultimatecalendar.sync.ManualRefresh
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshViewModelTest {
    private val running = MutableStateFlow(false)
    private val unsent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val refresher = mockk<ManualRefresh> {
        every { this@mockk.running } returns this@RefreshViewModelTest.running
    }
    private val syncs = mockk<InvitationSyncs> {
        every { unsent } returns this@RefreshViewModelTest.unsent
    }

    @BeforeEach
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private fun viewModel() = RefreshViewModel(refresher, syncs)

    @Test
    fun `it follows the refresh that is running`() = runTest {
        val model = viewModel()

        model.refreshing.test {
            assertEquals(false, awaitItem())
            running.value = true
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `a refresh tells how it went and a tap during one tells nothing`() = runTest {
        val report = RefreshReport(setOf(RefreshIssue.OFFLINE))
        coEvery { refresher.refresh() } returns report andThen null
        val model = viewModel()

        model.messages.test {
            model.refresh()
            assertEquals(ShellMessage.Refreshed(report), awaitItem())

            model.refresh()
            expectNoEvents()
        }
    }

    @Test
    fun `an invitation that waits for the account's own sync is told`() = runTest {
        val model = viewModel()

        model.messages.test {
            unsent.emit(Unit)

            assertEquals(ShellMessage.InvitationsLater, awaitItem())
        }
    }
}
