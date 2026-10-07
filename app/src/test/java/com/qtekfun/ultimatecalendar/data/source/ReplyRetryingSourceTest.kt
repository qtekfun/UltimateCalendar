// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.data.sync.AccountSyncState
import com.qtekfun.ultimatecalendar.data.sync.DeviceSyncState
import com.qtekfun.ultimatecalendar.data.sync.ReplyDelivery
import com.qtekfun.ultimatecalendar.data.sync.SyncEnvironment
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReplyRetryingSourceTest {
    private var networkAvailable = false
    private var retries = 0
    private val google = CalendarAccount("me@gmail.com", "com.google")
    private val calendar = CalendarInfo(CalendarId(1), google, "c", 0, CalendarAccess.OWNER)
    private val event = mockk<Event> { every { calendarId } returns CalendarId(1) }
    private val delegate = mockk<CalendarSource>()
    private val source = ReplyRetryingSource(
        delegate,
        ReplyDelivery(
            object : SyncEnvironment {
                override fun account(account: CalendarAccount) = AccountSyncState(true, true)

                override fun device() = DeviceSyncState(false, networkAvailable)
            },
            { retries++ }
        )
    )
    private val id = EventId(5)

    init {
        coEvery { delegate.respond(id, any()) } returns CalendarResult.Success(Unit)
        coEvery { delegate.event(id) } returns CalendarResult.Success(event)
        coEvery { delegate.calendars() } returns CalendarResult.Success(listOf(calendar))
    }

    @Test
    fun `an answer stored while the account cannot sync schedules the retry`() = runTest {
        assertEquals(CalendarResult.Success(Unit), source.respond(id, AttendeeStatus.ACCEPTED))
        assertEquals(1, retries)
    }

    @Test
    fun `an answer stored while the account can sync schedules nothing`() = runTest {
        networkAvailable = true
        source.respond(id, AttendeeStatus.ACCEPTED)
        assertEquals(0, retries)
    }

    @Test
    fun `a failed answer is passed on and schedules nothing`() = runTest {
        coEvery { delegate.respond(id, any()) } returns
            CalendarResult.Failure(CalendarError.NotFound)
        assertEquals(
            CalendarResult.Failure(CalendarError.NotFound),
            source.respond(id, AttendeeStatus.DECLINED)
        )
        assertEquals(0, retries)
    }

    @Test
    fun `an event or calendar that cannot be read leaves the answer as it is`() = runTest {
        coEvery { delegate.event(id) } returns CalendarResult.Failure(CalendarError.NotFound)
        assertEquals(CalendarResult.Success(Unit), source.respond(id, AttendeeStatus.ACCEPTED))
        coEvery { delegate.event(id) } returns CalendarResult.Success(event)
        coEvery { delegate.calendars() } returns CalendarResult.Failure(CalendarError.NotFound)
        source.respond(id, AttendeeStatus.ACCEPTED)
        coEvery { delegate.calendars() } returns CalendarResult.Success(emptyList())
        source.respond(id, AttendeeStatus.ACCEPTED)
        assertEquals(0, retries)
    }

    @Test
    fun `a wrapped source that cannot tell is never denied`() {
        assertEquals(false, source.denied.value)
    }
}
