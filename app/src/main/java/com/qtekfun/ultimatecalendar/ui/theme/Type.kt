// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val UltimateCalendarTypography = Typography()

/**
 * Text styles specific to a calendar, on top of Material's scale. All sizes are in sp, so they
 * follow the system font size; layouts must wrap content instead of fixing heights.
 */
@Immutable
data class CalendarTypography(
    /** "October 2026" in the top bar. */
    val monthTitle: TextStyle = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal
    ),
    /** The number in a day badge. */
    val dayNumber: TextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    /** "MON" above the day number. */
    val weekday: TextStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    ),
    /** "10 AM" in the hour gutter. */
    val hourLabel: TextStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    ),
    /** The title inside an event chip. */
    val eventTitle: TextStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    ),
    /** Time and place under the title of an event chip. */
    val eventDetail: TextStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Normal
    ),
    /** Small caption such as the week number. */
    val caption: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    )
)

internal val LocalCalendarTypography = staticCompositionLocalOf { CalendarTypography() }

/** The calendar text styles of the current theme. */
val MaterialTheme.calendarType: CalendarTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalCalendarTypography.current
