// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings

import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.domain.navigation.NavigationSettings
import com.qtekfun.ultimatecalendar.domain.settings.InitialView
import java.time.DayOfWeek
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the app shell and the widgets read from the settings (RF-10): the first day of the week
 * and the view the app opens on.
 */
@Singleton
class RepositoryNavigationSettings @Inject constructor(private val repository: SettingsRepository) :
    NavigationSettings {
    override fun current(): DayOfWeek = resolve(repository.current())

    override fun changes(): Flow<DayOfWeek> =
        repository.settings.map(::resolve).distinctUntilChanged()

    override fun initial(): CalendarView = repository.current().initialView.toCalendarView()

    private fun resolve(settings: AppSettings): DayOfWeek =
        settings.firstDayOfWeek.resolve(Locale.getDefault())
}

internal fun InitialView.toCalendarView(): CalendarView = when (this) {
    InitialView.AGENDA -> CalendarView.AGENDA
    InitialView.DAY -> CalendarView.DAY
    InitialView.WEEK -> CalendarView.WEEK
    InitialView.MONTH -> CalendarView.MONTH
}
