// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.sync.AccountSyncState
import com.qtekfun.ultimatecalendar.data.sync.DeviceSyncState
import com.qtekfun.ultimatecalendar.data.sync.ReplyDelivery
import com.qtekfun.ultimatecalendar.data.sync.SyncEnvironment
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReplyStatusTest {
    private val google = CalendarAccount("me@gmail.com", "com.google")
    private val calendar = CalendarInfo(CalendarId(1), google, "c", 0, CalendarAccess.OWNER)
    private val source = mockk<CalendarSource>()
    private val status = SourceReplyStatus(
        source,
        ReplyDelivery(
            object : SyncEnvironment {
                override fun account(account: CalendarAccount) = AccountSyncState(true, true)

                override fun device() = DeviceSyncState(false, networkAvailable = false)
            },
            { }
        )
    )

    @Test
    fun `an answer to a calendar of an account that cannot sync now is waiting`() = runTest {
        coEvery { source.calendars() } returns CalendarResult.Success(listOf(calendar))
        assertTrue(status.isWaiting(InvitationKey(CalendarId(1), EventId(7))))
    }

    @Test
    fun `an answer for another of my accounts waits on that account, not on the calendar's`() =
        runTest {
            val other = CalendarAccount("b@gmail.com", "com.google")
            val mine = CalendarInfo(
                CalendarId(1),
                CalendarAccount("a@gmail.com", "LOCAL"),
                "c",
                0,
                CalendarAccess.OWNER
            )
            val theirs = CalendarInfo(CalendarId(2), other, "d", 0, CalendarAccess.OWNER)
            coEvery { source.calendars() } returns CalendarResult.Success(listOf(mine, theirs))

            assertTrue(status.isWaiting(InvitationKey(CalendarId(1), EventId(7), "b@gmail.com")))
            assertFalse(status.isWaiting(InvitationKey(CalendarId(1), EventId(7), "c@gmail.com")))
            coEvery { source.calendars() } returns
                CalendarResult.Failure(CalendarError.PermissionDenied)
            assertFalse(status.isWaiting(InvitationKey(CalendarId(1), EventId(7), "b@gmail.com")))
        }

    @Test
    fun `an unknown calendar or an unreadable list is not waiting`() = runTest {
        coEvery { source.calendars() } returns CalendarResult.Success(listOf(calendar))
        assertFalse(status.isWaiting(InvitationKey(CalendarId(2), EventId(7))))
        coEvery { source.calendars() } returns
            CalendarResult.Failure(CalendarError.PermissionDenied)
        assertFalse(status.isWaiting(InvitationKey(CalendarId(1), EventId(7))))
    }
}
