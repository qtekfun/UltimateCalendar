// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

/** Whether any account has at least one calendar, to tell when the wizard must explain how. */
fun interface CalendarPresence {
    suspend fun hasCalendars(): Boolean
}
