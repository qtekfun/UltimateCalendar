// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the Android calendar provider can be read. The app works without it (CalDAV and
 * subscriptions need no calendar permission), so a missing permission is a state to show, not
 * an error that hides everything else: the shell offers to grant it for the Android accounts.
 */
interface ProviderAccess {
    /** True while the provider's last answer was "permission denied". */
    val denied: StateFlow<Boolean>
}
