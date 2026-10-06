// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.provider.CalendarContract
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What the system says about syncing the calendar of one account. */
data class AccountSyncState(
    /** False when the provider reports the account cannot sync calendars at all. */
    val syncable: Boolean,
    /** The account's "sync calendar" switch (`SYNC_EVENTS`) is on. */
    val syncsEvents: Boolean
)

/** What the device says about spending battery and data now. */
data class DeviceSyncState(val batterySaver: Boolean, val networkAvailable: Boolean)

/** Reads the state a sync request depends on. Unknown values count as "allowed". */
interface SyncEnvironment {
    fun account(account: CalendarAccount): AccountSyncState

    fun device(): DeviceSyncState
}

/** [SyncEnvironment] from the content resolver, the power manager and the connectivity manager. */
class AndroidSyncEnvironment @Inject constructor(@ApplicationContext private val context: Context) :
    SyncEnvironment {
    override fun account(account: CalendarAccount): AccountSyncState = try {
        val android = Account(account.name, account.type)
        AccountSyncState(
            // Negative means "not decided yet": the adapter will say when it runs.
            syncable = ContentResolver.getIsSyncable(android, CalendarContract.AUTHORITY) != 0,
            syncsEvents = ContentResolver.getSyncAutomatically(android, CalendarContract.AUTHORITY)
        )
    } catch (_: SecurityException) {
        AccountSyncState(syncable = true, syncsEvents = true)
    }

    override fun device(): DeviceSyncState {
        val power = context.getSystemService(PowerManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        return DeviceSyncState(
            batterySaver = power?.isPowerSaveMode == true,
            networkAvailable =
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        )
    }
}
