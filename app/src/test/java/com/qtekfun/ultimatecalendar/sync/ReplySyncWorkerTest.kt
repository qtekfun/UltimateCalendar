// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.sync.SourceSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.data.sync.SyncRequests
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReplySyncWorkerTest {
    private val google = CalendarAccount("me@gmail.com", "com.google")
    private val calendars = listOf(
        CalendarInfo(CalendarId(1), google, "g", 0, CalendarAccess.OWNER),
        CalendarInfo(CalendarId(2), CalendarAccount("x", "LOCAL"), "l", 0, CalendarAccess.OWNER),
        CalendarInfo(
            CalendarId(3),
            CalendarAccount("c", CalendarAccount.CALDAV_TYPE),
            "d",
            0,
            CalendarAccess.OWNER
        )
    )
    private val source = mockk<CalendarSource>()
    private val requester = mockk<SourceSyncRequester>()
    private var attempt = 0
    private val params = mockk<WorkerParameters> { every { runAttemptCount } answers { attempt } }
    private val worker = ReplySyncWorker(mockk(relaxed = true), params, source, requester)

    init {
        coEvery { source.calendars() } returns CalendarResult.Success(calendars)
    }

    @Test
    fun `it asks only the Android accounts, urgently, and is done when they were asked`() =
        runTest {
            coEvery { requester.requestSync(any(), any()) } returns SyncRequests(1, 0)
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
            coVerify { requester.requestSync(setOf(google), SyncReason.MANUAL) }
        }

    @Test
    fun `without an Android account there is nothing to ask`() = runTest {
        coEvery { source.calendars() } returns CalendarResult.Success(calendars.drop(1))
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        coVerify(exactly = 0) { requester.requestSync(any(), any()) }
    }

    @Test
    fun `a refused request or an unreadable list is retried a few times, then dropped`() = runTest {
        coEvery { requester.requestSync(any(), any()) } returns SyncRequests(0, 1)
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        coEvery { source.calendars() } returns
            CalendarResult.Failure(CalendarError.SourceFailure("down"))
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        attempt = ReplySyncWorker.MAX_ATTEMPTS - 1
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
