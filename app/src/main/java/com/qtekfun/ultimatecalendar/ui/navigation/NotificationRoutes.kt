// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import com.qtekfun.ultimatecalendar.notify.NotificationRoute
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where a tapped notification asked the app to go (RF-07). `MainActivity` publishes the route of
 * the intent that started or reached it; `AppNavigation` opens it and then [consume]s it, so a
 * rotation does not open it again.
 */
@Singleton
class NotificationRoutes @Inject constructor() {
    private val requested = MutableStateFlow<NotificationRoute?>(null)

    val pending: StateFlow<NotificationRoute?> get() = requested

    fun publish(route: NotificationRoute) {
        requested.value = route
    }

    fun consume() {
        requested.value = null
    }
}
