// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.settings

import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/** First day of the week: the locale's own, or one picked. */
enum class FirstDayOfWeek(private val day: DayOfWeek?) {
    LOCALE(null),
    SATURDAY(DayOfWeek.SATURDAY),
    SUNDAY(DayOfWeek.SUNDAY),
    MONDAY(DayOfWeek.MONDAY);

    fun resolve(locale: Locale): DayOfWeek = day ?: WeekFields.of(locale).firstDayOfWeek
}

/** The view shown when the app opens. */
enum class InitialView { AGENDA, DAY, WEEK, MONTH }

/** How often the invitations are looked for; [minutes] is null when only by hand. */
enum class InviteCheckInterval(val minutes: Int?) {
    EVERY_15(QUARTER_HOUR),
    EVERY_30(HALF_HOUR),
    EVERY_60(HOUR),
    MANUAL(null)
}

private const val QUARTER_HOUR = 15
private const val HALF_HOUR = 30
private const val HOUR = 60
