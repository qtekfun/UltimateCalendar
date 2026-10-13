// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.timegrid.GridZoom
import com.qtekfun.ultimatecalendar.domain.timegrid.GridZoomStore
import javax.inject.Inject
import javax.inject.Named

private const val KEY_GRID_ZOOM = "grid_zoom"

/**
 * The pinch zoom of the hour grid, next to the settings. It is not a setting the user picks in
 * Settings, so it is not part of [AppSettings] and does not travel in backups.
 */
class PreferencesGridZoomStore @Inject constructor(
    @Named(SettingsRepository.SETTINGS_PREFERENCES) private val preferences: SharedPreferences
) : GridZoomStore {
    override fun gridZoom(): Float =
        GridZoom.clamp(preferences.getFloat(KEY_GRID_ZOOM, GridZoom.DEFAULT))

    override fun saveGridZoom(zoom: Float) =
        preferences.edit { putFloat(KEY_GRID_ZOOM, GridZoom.clamp(zoom)) }
}
