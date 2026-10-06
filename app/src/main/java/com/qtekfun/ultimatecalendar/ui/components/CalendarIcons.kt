// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView

private const val VIEWPORT = 24f

private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, VIEWPORT, VIEWPORT)
        .addPath(PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))
        .build()

/**
 * Icons the app draws itself (no icon pack is allowed): one per calendar view, and Help. Paths
 * are 24 dp Material-style glyphs; the Help one is Material's `help_outline` (Apache-2.0).
 */
object CalendarIcons {
    val ViewAgenda: ImageVector by lazy {
        icon("ViewAgenda", "M4,5h16v3H4z M4,10.5h16v3H4z M4,16h16v3H4z")
    }

    val ViewDay: ImageVector by lazy { icon("ViewDay", "M6,4h12v16H6z") }

    val ViewThreeDays: ImageVector by lazy {
        icon("ViewThreeDays", "M3,4h5v16H3z M9.5,4h5v16h-5z M16,4h5v16h-5z")
    }

    val ViewWeek: ImageVector by lazy {
        icon(
            "ViewWeek",
            "M3,4h1.8v16H3z M6.6,4h1.8v16H6.6z M10.2,4h1.8v16h-1.8z M13.8,4h1.8v16h-1.8z " +
                "M17.4,4h1.8v16h-1.8z M21,4h-1.8v16H21z"
        )
    }

    val ViewMonth: ImageVector by lazy {
        icon(
            "ViewMonth",
            "M3,4h5v4H3z M9.5,4h5v4h-5z M16,4h5v4h-5z M3,10h5v4H3z M9.5,10h5v4h-5z " +
                "M16,10h5v4h-5z M3,16h5v4H3z M9.5,16h5v4h-5z M16,16h5v4h-5z"
        )
    }

    val Help: ImageVector by lazy {
        icon(
            "Help",
            "M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 " +
                "12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c" +
                "-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 " +
                "3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z"
        )
    }

    /** The glyph of a view, in the switcher and the drawer. */
    fun of(view: CalendarView): ImageVector = when (view) {
        CalendarView.AGENDA -> ViewAgenda
        CalendarView.DAY -> ViewDay
        CalendarView.THREE_DAYS -> ViewThreeDays
        CalendarView.WEEK -> ViewWeek
        CalendarView.MONTH -> ViewMonth
    }
}
