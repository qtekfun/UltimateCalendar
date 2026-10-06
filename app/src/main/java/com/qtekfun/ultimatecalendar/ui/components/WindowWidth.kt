// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Material window width classes, computed here because the official helper is another
 * dependency. The full adaptive layout is T24; until then the shell only caps its content width.
 */
enum class WindowWidth {
    /** Phones in portrait (under 600 dp). */
    COMPACT,

    /** Large phones in landscape, small tablets (600 to 839 dp). */
    MEDIUM,

    /** Tablets and desktop windows (840 dp and up). */
    EXPANDED;

    companion object {
        private const val MEDIUM_FROM_DP = 600
        private const val EXPANDED_FROM_DP = 840

        fun of(widthDp: Int): WindowWidth = when {
            widthDp >= EXPANDED_FROM_DP -> EXPANDED
            widthDp >= MEDIUM_FROM_DP -> MEDIUM
            else -> COMPACT
        }
    }
}

/** The width class of the current window. */
@Composable
fun currentWindowWidth(): WindowWidth {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current.density
    return WindowWidth.of((widthPx / density).toInt())
}
