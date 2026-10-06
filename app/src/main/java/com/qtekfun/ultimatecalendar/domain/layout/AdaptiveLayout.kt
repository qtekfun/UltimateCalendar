// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.layout

import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView

/** How the calendars list and the views are reached. */
enum class NavigationStyle {
    /** A drawer that slides over the content, opened from the header. */
    MODAL_DRAWER,

    /** A drawer that is always visible next to the content. */
    PERMANENT_DRAWER
}

/**
 * Which layout the window gets (RF-03, adaptive tablet layout), decided from its width alone.
 * It is a pure function of the width, so it is the same after a rotation, a window resize, a
 * split screen or a fold: nothing is remembered between sizes and nothing needs hysteresis.
 */
data class AdaptiveLayout(val widthClass: WidthClass) {
    private val expanded get() = widthClass == WidthClass.EXPANDED

    val navigation: NavigationStyle
        get() = if (expanded) NavigationStyle.PERMANENT_DRAWER else NavigationStyle.MODAL_DRAWER

    /** The Agenda is a list with the selected event's detail beside it. */
    val agendaTwoPane: Boolean get() = expanded

    /** Search and the invitations tray open over the shell in a dialog, not as a full screen. */
    val overlaysAsDialogs: Boolean get() = expanded

    /**
     * The event detail goes in the Agenda's second pane: the Agenda is on screen, the window is
     * wide enough, and nothing else (the search or the tray, which are on top) is open.
     */
    fun detailInPane(view: CalendarView, overlayOpen: Boolean): Boolean =
        agendaTwoPane && view == CalendarView.AGENDA && !overlayOpen

    /**
     * The widest the shell's content grows before it is centered, or null to fill the window.
     * A single day or a plain list is not worth stretching; three days, a week and a month are.
     */
    fun contentMaxWidthDp(view: CalendarView): Int? = when {
        widthClass == WidthClass.COMPACT -> WIDE_MAX_DP
        view == CalendarView.DAY -> WIDE_MAX_DP
        view == CalendarView.AGENDA && !agendaTwoPane -> WIDE_MAX_DP
        else -> null
    }

    /**
     * How much bigger the hour grid draws its text and hours than on a phone. It rides on the
     * font scale, which the grid already uses for its sizes, so the grid stays consistent.
     */
    val gridScale: Float
        get() = when (widthClass) {
            WidthClass.COMPACT -> 1f
            WidthClass.MEDIUM -> MEDIUM_GRID_SCALE
            WidthClass.EXPANDED -> EXPANDED_GRID_SCALE
        }

    /**
     * The factor to multiply the user's [fontScale] by in the grid: [gridScale], but never so
     * much that the total goes over the grid's own limit of 200 %, and never below 1.
     */
    fun gridTextFactor(fontScale: Float): Float =
        minOf(gridScale, MAX_GRID_FONT_SCALE / fontScale).coerceAtLeast(1f)

    companion object {
        /** Widest a settings page, the detail or the editor gets (the reading width). */
        const val READING_MAX_DP = 600

        /** Widest a day or a list gets, a bit more than the reading width. */
        const val WIDE_MAX_DP = 840

        private const val MEDIUM_GRID_SCALE = 1.15f
        private const val EXPANDED_GRID_SCALE = 1.3f
        private const val MAX_GRID_FONT_SCALE = 2f

        fun of(widthDp: Int): AdaptiveLayout = AdaptiveLayout(WidthClass.of(widthDp))
    }
}
