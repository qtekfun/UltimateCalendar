// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.domain.subscriptions.SchedulePlan

/**
 * When subscriptions are refreshed in the background. Nothing is scheduled while there is nothing
 * to refresh by itself, so a user without subscriptions pays nothing.
 */
interface SubscriptionScheduler {
    /** Makes the periodic work match [plan]: started, changed or cancelled. */
    fun apply(plan: SchedulePlan)

    /** A refresh of every enabled subscription as soon as there is a connection. */
    fun refreshNow()
}
