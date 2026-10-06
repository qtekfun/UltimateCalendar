// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs [KeepAliveService] exactly while robust mode is on: it starts when the mode is turned on
 * and stops when it is turned off.
 */
@Singleton
class KeepAliveController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: ReminderSettingsSource
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.settings.map { it.robustMode }
                .distinctUntilChanged()
                .collect { wanted ->
                    if (wanted) KeepAliveService.start(context) else KeepAliveService.stop(context)
                }
        }
    }

    /** Whether the service should run now, for the boot receiver. */
    suspend fun wanted(): Boolean = settings.settings.first().robustMode
}
