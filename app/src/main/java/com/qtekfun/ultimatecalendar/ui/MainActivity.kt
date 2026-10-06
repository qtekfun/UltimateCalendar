// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.domain.firstrun.FirstRunFlag
import com.qtekfun.ultimatecalendar.ui.firstrun.FirstRunHost
import com.qtekfun.ultimatecalendar.ui.navigation.AppNavigation
import com.qtekfun.ultimatecalendar.ui.theme.UltimateCalendarTheme
import com.qtekfun.ultimatecalendar.ui.theme.toThemeOptions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var firstRun: FirstRunFlag

    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val options by remember { settings.settings.map { it.toThemeOptions() } }
                .collectAsStateWithLifecycle(settings.current().toThemeOptions())
            UltimateCalendarTheme(options) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FirstRunHost(firstRun) { AppNavigation() }
                }
            }
        }
    }
}
