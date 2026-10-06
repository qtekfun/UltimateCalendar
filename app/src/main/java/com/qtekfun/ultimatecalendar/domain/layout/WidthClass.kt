// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.layout

/**
 * Material window width classes, computed here because the official helper is another
 * dependency. The thresholds are the ones of the Material guidelines.
 */
enum class WidthClass {
    /** Phones in portrait (under 600 dp). */
    COMPACT,

    /** Large phones in landscape, small tablets, a folded-out foldable (600 to 839 dp). */
    MEDIUM,

    /** Tablets and desktop windows (840 dp and up). */
    EXPANDED;

    companion object {
        private const val MEDIUM_FROM_DP = 600
        private const val EXPANDED_FROM_DP = 840

        fun of(widthDp: Int): WidthClass = when {
            widthDp >= EXPANDED_FROM_DP -> EXPANDED
            widthDp >= MEDIUM_FROM_DP -> MEDIUM
            else -> COMPACT
        }
    }
}
