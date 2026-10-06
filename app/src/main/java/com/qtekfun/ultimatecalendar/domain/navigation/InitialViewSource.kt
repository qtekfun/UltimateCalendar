// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

/** The view the app opens on when nothing else was selected (RF-10). */
fun interface InitialViewSource {
    fun initial(): CalendarView
}
