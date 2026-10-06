// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import android.content.ContentResolver
import android.database.Cursor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderCalendarPresenceTest {
    private val resolver = mockk<ContentResolver>()

    private fun presence(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        ProviderCalendarPresence(resolver, StandardTestDispatcher(scheduler))

    private fun cursor(rows: Int) = mockk<Cursor>(relaxed = true) { every { count } returns rows }

    @Test
    fun `there are calendars when the provider returns rows, and the cursor is closed`() = runTest {
        val rows = cursor(2)
        every { resolver.query(any(), any(), any(), any(), any<String>()) } returns rows
        assertTrue(presence(testScheduler).hasCalendars())
        verify { rows.close() }
    }

    @Test
    fun `there are none when the provider returns no rows`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any<String>()) } returns cursor(0)
        assertFalse(presence(testScheduler).hasCalendars())
    }

    @Test
    fun `there are none when the provider gives no cursor`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any<String>()) } returns null
        assertFalse(presence(testScheduler).hasCalendars())
    }

    @Test
    fun `without the permission it counts as none`() = runTest {
        every {
            resolver.query(any(), any(), any(), any(), any<String>())
        } throws SecurityException("no READ_CALENDAR")
        assertFalse(presence(testScheduler).hasCalendars())
    }
}
