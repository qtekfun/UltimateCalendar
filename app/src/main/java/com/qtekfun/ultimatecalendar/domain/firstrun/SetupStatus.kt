// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

/**
 * What the system allows right now, as the wizard needs to know it. [hasCalendars] is null while
 * it cannot be known (no calendar permission yet). [otherCalendarApps] are the package names of
 * the other calendar apps found on the phone.
 */
data class SetupStatus(
    val calendarPermission: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val batteryExempt: Boolean,
    val hasCalendars: Boolean?,
    val maker: PhoneMaker,
    val otherCalendarApps: List<String> = emptyList()
)
