// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import java.time.ZoneId

/** The phone's time zone now: it can change while the app runs, so it is asked every time. */
fun interface SystemZone {
    fun current(): ZoneId
}
