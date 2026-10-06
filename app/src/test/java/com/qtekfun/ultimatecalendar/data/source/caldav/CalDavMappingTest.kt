// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.source.CalDavIds
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalDavMappingTest {
    private val account = DavAccountEntity(
        id = 4,
        serverUrl = "https://cloud.example.com/nextcloud/",
        loginName = "ana",
        userAddresses = "Ana@Example.com,ana@work.example"
    )

    private fun calendar(color: String? = "#FF9500", writable: Boolean = true) = DavCalendarEntity(
        id = 9,
        accountId = 4,
        href = "/c/",
        name = "Work",
        color = color,
        writable = writable
    )

    @Test
    fun `a calendar carries a CalDAV id, the account of the server and the user's address`() {
        val info = CalDavMapping.calendar(calendar(), account)

        assertEquals(CalDavIds.calendar(9), info.id)
        assertEquals(
            CalendarAccount("ana@cloud.example.com", CalendarAccount.CALDAV_TYPE),
            info.account
        )
        assertTrue(info.account.isCalDav)
        assertFalse(info.account.isLocal)
        assertEquals("Work", info.displayName)
        assertEquals("Ana@Example.com", info.ownerEmail)
        assertEquals(0xFFFF9500.toInt(), info.color)
    }

    @Test
    fun `a calendar the user cannot write to is read-only`() {
        assertEquals(CalendarAccess.OWNER, CalDavMapping.calendar(calendar(), account).access)
        assertEquals(
            CalendarAccess.READ,
            CalDavMapping.calendar(calendar(writable = false), account).access
        )
    }

    @Test
    fun `an unreadable or missing color gets the neutral blue`() {
        val blue = 0xFF0B63CE.toInt()
        listOf(null, "", "red", "#12", "#GGGGGG", "FF9500").forEach {
            assertEquals(blue, CalDavMapping.color(it), "$it")
        }
        assertEquals(0xFF000001.toInt(), CalDavMapping.color("#000001"))
    }

    @Test
    fun `the addresses are the ones discovery found, or else the login when it is an address`() {
        assertEquals(
            listOf("Ana@Example.com", "ana@work.example"),
            CalDavMapping.addresses(account)
        )
        val bare = account.copy(userAddresses = "")
        assertEquals(emptyList<String>(), CalDavMapping.addresses(bare))
        assertEquals(
            listOf("ana@example.com"),
            CalDavMapping.addresses(bare.copy(loginName = " Ana@Example.com "))
        )
    }

    @Test
    fun `a server address that is not a URL is the account name as it is`() {
        assertEquals(
            "ana@not a url",
            CalDavMapping.accountName(account.copy(serverUrl = "not a url"))
        )
    }

    @Test
    fun `new unique ids are different and name an ics resource of the app`() {
        val factory = RandomUidFactory()

        val first = factory.next()

        assertTrue(Regex("[0-9a-f-]{36}@ultimatecalendar").matches(first), first)
        assertFalse(first == factory.next())
    }
}
