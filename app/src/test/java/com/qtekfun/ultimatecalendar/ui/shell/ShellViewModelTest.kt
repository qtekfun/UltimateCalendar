// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.ProviderAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.domain.navigation.NavigationSettings
import com.qtekfun.ultimatecalendar.domain.navigation.PendingInvitations
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.SystemZone
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    // 23:30 UTC on the 10th is already the 11th in Madrid: the zone decides "today".
    private val clock = Clock.fixed(Instant.parse("2026-03-10T23:30:00Z"), ZoneId.of("UTC"))
    private val today = LocalDate.parse("2026-03-11")
    private val google = CalendarAccount("me@example.com", "com.google")
    private val dav = CalendarAccount("Nextcloud", "bitfire.at.davdroid")
    private val work = calendar(1, "Work", google)
    private val family = calendar(2, "Family", dav)

    private lateinit var database: UltimateCalendarDatabase
    private lateinit var source: FakeCalendarSource
    private lateinit var repository: CalendarRepository
    private val invitations = MutableStateFlow(0)
    private var firstDay = DayOfWeek.MONDAY
    private var initial = CalendarView.WEEK
    private val main = UnconfinedTestDispatcher()

    @BeforeEach
    fun open() {
        Dispatchers.setMain(main)
        database = inMemoryDatabase()
        source = FakeCalendarSource(listOf(work, family))
        repository = repositoryOver(source)
    }

    @AfterEach
    fun close() {
        // Let every WhileSubscribed upstream stop before the database goes away.
        main.scheduler.advanceTimeBy(STOP_TIMEOUT_MS)
        database.close()
        // No resetMain: the cancelled upstreams finish on Main from another thread, after this.
    }

    private val providerDenied = MutableStateFlow(false)

    private fun calendar(id: Long, name: String, account: CalendarAccount) =
        CalendarInfo(CalendarId(id), account, name, 0xFF0B63CE.toInt(), CalendarAccess.OWNER)

    private fun repositoryOver(source: CalendarSource) = CalendarRepository(
        object : CalendarSource by source, ProviderAccess {
            override val denied: StateFlow<Boolean> = providerDenied
        },
        database.calendarSettingsDao(),
        Dispatchers.Unconfined
    )

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = ShellViewModel(
        saved,
        repository,
        PendingInvitations { invitations },
        clock,
        SystemZone { madrid },
        object : NavigationSettings {
            override fun current() = firstDay

            override fun initial() = initial
        }
    )

    /** The first state from here on that [matches]: the calendars load on another thread. */
    private suspend fun ReceiveTurbine<ShellUiState>.awaitUntil(
        matches: (ShellUiState) -> Boolean
    ): ShellUiState {
        while (true) {
            val state = awaitItem()
            if (matches(state)) return state
        }
    }

    private suspend fun ReceiveTurbine<ShellUiState>.loaded() =
        awaitUntil { it.accounts.isNotEmpty() || it.calendarsFailed }

    private companion object {
        const val STOP_TIMEOUT_MS = 6_000L
        const val VERIFY_TIMEOUT_MS = 2_000L
    }

    @Test
    fun `it starts on the week of today`() = runTest {
        viewModel().state.test {
            val state = awaitItem()
            assertEquals(CalendarView.WEEK, state.view)
            assertEquals(today, state.date)
            assertEquals(today, state.today)
            assertEquals(
                DateRange(LocalDate.parse("2026-03-09"), LocalDate.parse("2026-03-16")),
                state.range
            )
            assertEquals(DayOfWeek.MONDAY, state.firstDayOfWeek)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it opens on the view chosen in Settings, unless one was already selected`() = runTest {
        initial = CalendarView.MONTH

        viewModel().state.test {
            assertEquals(CalendarView.MONTH, awaitItem().view)
            cancelAndIgnoreRemainingEvents()
        }
        val saved = SavedStateHandle(mapOf("view" to "DAY"))
        viewModel(saved).state.test {
            assertEquals(CalendarView.DAY, awaitItem().view)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `weeks follow the first day of the week`() = runTest {
        firstDay = DayOfWeek.SUNDAY

        viewModel().state.test {
            val state = awaitItem()
            assertEquals(LocalDate.parse("2026-03-08"), state.range.start)
            assertEquals(DayOfWeek.SUNDAY, state.firstDayOfWeek)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a view changes the range around the same date`() = runTest {
        val model = viewModel()

        model.state.test {
            awaitItem()
            model.selectView(CalendarView.MONTH)
            val month = awaitUntil { it.view == CalendarView.MONTH }
            assertEquals(today, month.date)
            assertEquals(
                DateRange(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-04-01")),
                month.range
            )

            model.selectView(CalendarView.THREE_DAYS)
            val three = awaitUntil { it.view == CalendarView.THREE_DAYS }
            assertEquals(DateRange(today, LocalDate.parse("2026-03-14")), three.range)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `previous and next move by the period of the view`() = runTest {
        val model = viewModel()

        model.state.test {
            awaitItem()
            model.next()
            awaitUntil { it.date == LocalDate.parse("2026-03-18") }
            model.previous()
            model.previous()
            awaitUntil { it.date == LocalDate.parse("2026-03-04") }

            model.selectView(CalendarView.MONTH)
            model.next()
            awaitUntil { it.date == LocalDate.parse("2026-04-04") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a picked date and today`() = runTest {
        val model = viewModel()

        model.state.test {
            awaitItem()
            model.selectDate(LocalDate.parse("2028-02-29"))
            val picked = awaitUntil { it.date == LocalDate.parse("2028-02-29") }
            assertEquals(today, picked.today)

            model.goToToday()
            awaitUntil { it.date == today }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the selection survives being recreated`() = runTest {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        first.selectView(CalendarView.DAY)
        first.selectDate(LocalDate.parse("2026-12-24"))

        viewModel(saved).state.test {
            val state = awaitItem()
            assertEquals(CalendarView.DAY, state.view)
            assertEquals(LocalDate.parse("2026-12-24"), state.date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unknown saved view falls back to the week`() = runTest {
        val saved = SavedStateHandle(mapOf("view" to "DECADE", "date" to 0L))

        viewModel(saved).state.test {
            val state = awaitItem()
            assertEquals(CalendarView.WEEK, state.view)
            assertEquals(LocalDate.ofEpochDay(0), state.date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `calendars are grouped by account`() = runTest {
        viewModel().state.test {
            val state = loaded()
            assertEquals(listOf(google, dav), state.accounts.map { it.account })
            assertEquals(listOf("Work"), state.accounts[0].calendars.map { it.displayName })
            assertFalse(state.calendarsFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hiding a calendar writes the local override and shows it unchecked`() = runTest {
        val model = viewModel()

        model.state.test {
            loaded()
            model.setCalendarVisible(work.id, false)

            awaitUntil { it.isVisible(work.id) == false }
            assertEquals(false, repository.settings(work.id).visible)
            // The source itself was not touched.
            assertTrue(source.calendars().value.first { it.id == work.id }.visible)

            model.setCalendarVisible(work.id, true)
            awaitUntil { it.isVisible(work.id) == true }
            assertEquals(true, repository.settings(work.id).visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun ShellUiState.isVisible(id: CalendarId) =
        accounts.flatMap { it.calendars }.firstOrNull { it.id == id }?.visible

    @Test
    fun `the invitation count follows the source`() = runTest {
        viewModel().state.test {
            assertEquals(0, awaitItem().pendingInvitations)
            invitations.value = 3
            awaitUntil { it.pendingInvitations == 3 }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a source that fails is reported, not thrown`() = runTest {
        val failing = mockk<CalendarSource>()
        every { failing.changes } returns emptyFlow()
        coEvery { failing.calendars() } returns
            CalendarResult.Failure(CalendarError.PermissionDenied)
        repository = repositoryOver(failing)

        viewModel().state.test {
            val state = loaded()
            assertTrue(state.calendarsFailed)
            assertTrue(state.accounts.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the state tells when the phone's calendars cannot be read, whatever else loads`() =
        runTest {
            viewModel().state.test {
                assertFalse(awaitItem().calendarPermissionMissing)

                providerDenied.value = true
                assertTrue(awaitUntil { it.calendarPermissionMissing }.calendarPermissionMissing)

                providerDenied.value = false
                assertFalse(awaitUntil { !it.calendarPermissionMissing }.calendarPermissionMissing)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `answering the permission dialog reads the calendars again`() = runTest {
        val counting = mockk<CalendarSource>()
        every { counting.changes } returns emptyFlow()
        coEvery { counting.calendars() } returns CalendarResult.Success(emptyList())
        repository = repositoryOver(counting)
        val model = viewModel()

        model.state.test {
            awaitItem()
            coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { counting.calendars() }

            model.calendarPermissionAnswered()

            // The state is the same, so no new item: the read is what changed.
            coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 2) { counting.calendars() }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
