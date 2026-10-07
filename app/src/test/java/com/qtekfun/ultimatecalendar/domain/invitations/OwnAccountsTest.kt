// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OwnAccountsTest {
    private fun calendar(
        id: Long,
        account: String,
        owner: String? = null,
        access: CalendarAccess = CalendarAccess.OWNER,
        type: String = "com.google"
    ) = CalendarInfo(
        CalendarId(id),
        CalendarAccount(account, type),
        "c$id",
        0,
        access,
        ownerEmail = owner
    )

    @Test
    fun `the addresses are the accounts and the owners of the calendars, once and in lowercase`() {
        val calendars = listOf(
            calendar(1, "A@Gmail.com", owner = "a@gmail.com"),
            calendar(2, "a@gmail.com", owner = "a@gmail.com"),
            calendar(3, "b@gmail.com"),
            // An account that is not an address names its owner.
            calendar(4, "Nextcloud", owner = "c@work.org", type = "bitfire.at.davdroid"),
            calendar(5, "d@mail.org", owner = null)
        )

        assertEquals(
            setOf("a@gmail.com", "b@gmail.com", "c@work.org", "d@mail.org"),
            OwnAccounts.addresses(calendars)
        )
    }

    @Test
    fun `on-device and subscription accounts have no address of their own`() {
        val calendars = listOf(
            calendar(1, "me@local.org", type = "LOCAL"),
            calendar(2, "feed@x.org", type = CalendarAccount.SUBSCRIPTION_TYPE)
        )

        assertEquals(emptySet<String>(), OwnAccounts.addresses(calendars))
    }

    @Test
    fun `the owner of a calendar shared with me or read only is somebody else`() {
        val calendars = listOf(
            // The owner of a shared calendar differs from the account that shows it.
            calendar(1, "me@gmail.com", owner = "boss@gmail.com"),
            calendar(2, "Nextcloud", owner = "ro@work.org", access = CalendarAccess.READ)
        )

        assertEquals(setOf("me@gmail.com"), OwnAccounts.addresses(calendars))
    }

    @Test
    fun `group, holiday and resource calendars are nobody`() {
        val calendars = listOf(
            calendar(1, "t", owner = "t@group.calendar.google.com"),
            calendar(2, "t", owner = "t@group.v.calendar.google.com"),
            calendar(3, "t", owner = "#holiday@group.v.calendar.google.com"),
            calendar(4, "t", owner = "r@resource.calendar.google.com"),
            calendar(5, "x@group.calendar.google.com")
        )

        assertEquals(emptySet<String>(), OwnAccounts.addresses(calendars))
        assertTrue(OwnAccounts.isShared("t@group.calendar.google.com"))
        assertFalse(OwnAccounts.isShared("t@gmail.com"))
    }

    @Test
    fun `the calendars of an address are found by account or owner`() {
        val calendars = listOf(
            calendar(1, "a@gmail.com", owner = "a@gmail.com"),
            calendar(2, "b@gmail.com"),
            calendar(3, "Nextcloud", owner = "b@gmail.com")
        )

        assertEquals(
            listOf(CalendarId(2), CalendarId(3)),
            OwnAccounts.calendarsOf(calendars, "b@gmail.com").map { it.id }
        )
        assertEquals(emptyList<CalendarInfo>(), OwnAccounts.calendarsOf(calendars, "c@x.org"))
    }
}
