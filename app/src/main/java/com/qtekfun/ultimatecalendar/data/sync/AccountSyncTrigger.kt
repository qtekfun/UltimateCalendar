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
    fun request(account: CalendarAccount, expedited: Boolean)
}

/**
 * A background request is a normal one, which the system may batch with other syncs and delay.
 * Only an `expedited` one (the user asked) is manual and urgent, as the system's own "sync now".
 */
class ContentResolverSyncTrigger @Inject constructor() : AccountSyncTrigger {
    override fun request(account: CalendarAccount, expedited: Boolean) {
        val extras = Bundle().apply {
            if (expedited) {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
        }
        ContentResolver.requestSync(
            Account(account.name, account.type),
            CalendarContract.AUTHORITY,
            extras
        )
    }
}
