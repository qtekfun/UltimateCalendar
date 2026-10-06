// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.settings.FakePreferences
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PreferencesLastSyncStoreTest {
    private val preferences = FakePreferences()

    @Test
    fun `remembers the last good sync across instances until cleared`() {
        val at = Instant.parse("2026-10-06T08:15:00Z")
        assertNull(PreferencesLastSyncStore(preferences).lastOk())

        PreferencesLastSyncStore(preferences).recordOk(at)
        assertEquals(at, PreferencesLastSyncStore(preferences).lastOk())

        PreferencesLastSyncStore(preferences).clear()
        assertNull(PreferencesLastSyncStore(preferences).lastOk())
    }
}
