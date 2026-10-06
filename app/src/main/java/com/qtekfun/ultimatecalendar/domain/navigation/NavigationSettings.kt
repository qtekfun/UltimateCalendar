// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

/** What Settings decides about the shell (RF-10): the first day of the week and the opening view. */
interface NavigationSettings :
    FirstDayOfWeekSource,
    InitialViewSource
