// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeScale

private const val BASE_HOUR_HEIGHT = 64f
private const val MAX_FONT_SCALE = 2f
private val BASE_GUTTER = 56.dp

/**
 * The sizes of the hour grid, shared by Day, 3 days and (T16) Week. They grow with the font
 * scale so that text keeps fitting at 200 %: [scale] maps minutes to dp, [gutter] is the width
 * of the hour labels.
 */
internal data class GridMetrics(val scale: TimeScale, val gutter: Dp)

@Composable
internal fun rememberGridMetrics(): GridMetrics {
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, MAX_FONT_SCALE)
    return remember(fontScale) {
        GridMetrics(TimeScale(BASE_HOUR_HEIGHT * fontScale), BASE_GUTTER * fontScale)
    }
}
