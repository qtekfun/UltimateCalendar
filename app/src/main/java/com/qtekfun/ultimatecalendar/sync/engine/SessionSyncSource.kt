// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDavProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Syncs the account that is signed in to the built-in CalDAV. */
class SessionSyncSource @Inject constructor(
    private val session: AccountSession,
    private val provider: CalDavProvider
) : SyncSource {
    override suspend fun open(): SyncSession? {
        // A background sync may start in a fresh process, before the UI loaded the credentials.
        if (session.credentials() == null) session.restore()
        val account = session.activeAccount.first() ?: return null
        return provider.connect(account)?.let { SyncSession(account, it) }
    }
}
