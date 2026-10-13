// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreferencesGridZoomStoreTest {
    private val prefs = FakePreferences()
    private val store = PreferencesGridZoomStore(prefs)

    @Test
    fun `a new install has the plain zoom`() {
        assertEquals(1f, store.gridZoom())
    }

    @Test
    fun `the zoom is kept for the next launch`() {
        store.saveGridZoom(1.8f)
        assertEquals(1.8f, PreferencesGridZoomStore(prefs).gridZoom())
    }

    @Test
    fun `the zoom stays within its limits when saved and when read`() {
        store.saveGridZoom(40f)
        assertEquals(2.5f, store.gridZoom())
        prefs.values["grid_zoom"] = 0.01f
        assertEquals(0.5f, store.gridZoom())
    }

    @Test
    fun `it is not a setting and does not change them`() {
        store.saveGridZoom(2f)
        val settings = SettingsRepository(prefs, FakePreferences(), FakePreferences())
        assertEquals(AppSettings(), settings.current())
    }
}
