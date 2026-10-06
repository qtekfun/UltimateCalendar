// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import android.accounts.Account
import android.content.ContentResolver
import android.os.Bundle
import android.provider.CalendarContract
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import javax.inject.Inject

/** The one call to the system: asks the sync of the calendar provider for an account. */
fun interface AccountSyncTrigger {
    /** Throws [SecurityException] or [IllegalArgumentException] if the system refuses. */
    fun request(account: CalendarAccount)
}

/** Manual and expedited, as the system's own "sync now": it is not a periodic sync. */
class ContentResolverSyncTrigger @Inject constructor() : AccountSyncTrigger {
    override fun request(account: CalendarAccount) {
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        ContentResolver.requestSync(
            Account(account.name, account.type),
            CalendarContract.AUTHORITY,
            extras
        )
    }
}
