// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.data.settings.FakePreferences
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PreferencesSyncRequestLogTest {
    private val preferences = FakePreferences()
    private val google = CalendarAccount("me@gmail.com", "com.google")
    private val dav = CalendarAccount("me@gmail.com", "bitfire.at.davdroid")
    private val at = Instant.parse("2026-06-10T12:00:00Z")

    @Test
    fun `an account never asked has no record`() {
        assertNull(PreferencesSyncRequestLog(preferences).lastRequest(google))
    }

    @Test
    fun `the time is kept per account and survives a new instance`() {
        PreferencesSyncRequestLog(preferences).record(listOf(google), at)

        val reopened = PreferencesSyncRequestLog(preferences)
        assertEquals(at, reopened.lastRequest(google))
        assertNull(reopened.lastRequest(dav))
    }

    @Test
    fun `a later request replaces the time of the account`() {
        val log = PreferencesSyncRequestLog(preferences)
        log.record(listOf(google, dav), at)

        log.record(listOf(google), at.plusSeconds(60))

        assertEquals(at.plusSeconds(60), log.lastRequest(google))
        assertEquals(at, log.lastRequest(dav))
    }
}
