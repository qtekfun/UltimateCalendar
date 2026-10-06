// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

/** How a day number is highlighted. */
enum class DayBadgeState {
    NORMAL,

    /** Today: a solid primary circle, as in Google Calendar. */
    TODAY,

    /** The selected day (not today): a tonal circle. */
    SELECTED,

    /** A day outside the month shown. */
    DIMMED
}
