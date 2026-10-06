// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccountCalendarsTest {
    private val google = CalendarAccount("me@example.com", "com.google")
    private val dav = CalendarAccount("Nextcloud", "bitfire.at.davdroid")

    private fun calendar(id: Long, name: String, account: CalendarAccount) =
        CalendarInfo(CalendarId(id), account, name, 0xFF0B63CE.toInt(), CalendarAccess.OWNER)

    @Test
    fun `calendars are grouped by account, in alphabetical order`() {
        val groups = AccountCalendars.group(
            listOf(
                calendar(1, "work", google),
                calendar(2, "Personal", dav),
                calendar(3, "Birthdays", google),
                calendar(4, "Family", dav)
            )
        )

        assertEquals(listOf(google, dav), groups.map { it.account })
        assertEquals(listOf("Birthdays", "work"), groups[0].calendars.map { it.displayName })
        assertEquals(listOf("Family", "Personal"), groups[1].calendars.map { it.displayName })
    }

    @Test
    fun `accounts with the same name are told apart by type`() {
        val a = CalendarAccount("same", "b.type")
        val b = CalendarAccount("same", "a.type")
        val groups = AccountCalendars.group(listOf(calendar(1, "One", a), calendar(2, "Two", b)))

        assertEquals(listOf(b, a), groups.map { it.account })
    }

    @Test
    fun `no calendars make no groups`() {
        assertTrue(AccountCalendars.group(emptyList()).isEmpty())
    }
}
