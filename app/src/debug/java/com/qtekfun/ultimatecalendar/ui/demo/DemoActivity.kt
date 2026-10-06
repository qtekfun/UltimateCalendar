// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import com.qtekfun.ultimatecalendar.domain.layout.AdaptiveLayout
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.ui.adaptive.LocalAdaptiveLayout
import com.qtekfun.ultimatecalendar.ui.theme.ThemeOptions
import com.qtekfun.ultimatecalendar.ui.theme.UltimateCalendarTheme

/**
 * Debug-only host that shows the app's UI over invented data ([DemoData]) and never reads the
 * phone's calendar provider. Start it from adb, for example:
 *
 *     adb shell am start -n com.qtekfun.ultimatecalendar/.ui.demo.DemoActivity \
 *         --es screen gallery --es theme amoled --ez drawer true
 *
 * Extras: `screen` (shell, gallery), `theme` (system, light, dark, amoled), `view` (a
 * `CalendarView` name), `drawer`, `weeks`, `empty`, `dynamic` (booleans), `offset` (days) and
 * `width` (dp: 360 phone, 600 7" tablet, 1280 10" tablet; forces that window's layout).
 */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val options = DemoOptions.of(intent)
        setContent {
            UltimateCalendarTheme(options.theme) {
                // `width` forces the layout of that window width (dp), to see the tablet
                // layouts on a phone; without it the layout follows the window.
                CompositionLocalProvider(
                    LocalAdaptiveLayout provides options.widthDp?.let { AdaptiveLayout.of(it) }
                ) {
                    if (options.gallery) DemoGallery() else DemoShell(options)
                }
            }
        }
    }
}

/** What the intent asked the demo to show. */
internal data class DemoOptions(
    val gallery: Boolean,
    val theme: ThemeOptions,
    val view: CalendarView,
    val drawer: Boolean,
    val weeks: Boolean,
    val empty: Boolean,
    val offsetDays: Int,
    val widthDp: Int?
) {
    companion object {
        fun of(intent: Intent): DemoOptions {
            val name = intent.getStringExtra("theme") ?: "system"
            val dynamic = intent.getBooleanExtra("dynamic", false)
            val mode = when (name) {
                "light" -> ThemeMode.LIGHT
                "dark", "amoled" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            val view = intent.getStringExtra("view")
                ?.let { value -> CalendarView.entries.firstOrNull { it.name == value.uppercase() } }
            return DemoOptions(
                gallery = intent.getStringExtra("screen") == "gallery",
                theme = ThemeOptions(mode, amoled = name == "amoled", dynamicColor = dynamic),
                view = view ?: CalendarView.AGENDA,
                drawer = intent.getBooleanExtra("drawer", false),
                weeks = intent.getBooleanExtra("weeks", false),
                empty = intent.getBooleanExtra("empty", false),
                offsetDays = intent.getIntExtra("offset", 0),
                widthDp = intent.getIntExtra("width", 0).takeIf { it > 0 }
            )
        }
    }
}
