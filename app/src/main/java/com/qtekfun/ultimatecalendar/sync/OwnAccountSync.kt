// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome

/** The app's own CalDAV account syncing now, in the caller's coroutine ("sync now" buttons). */
fun interface OwnAccountSync {
    suspend fun syncNow(): SyncOutcome
}
