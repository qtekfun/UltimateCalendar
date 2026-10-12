// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import android.accounts.Account
import android.content.ContentResolver
import android.os.Build
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
 * An `expedited` one (the user asked, or answered an invitation) is manual and urgent, as the
 * system's own "sync now".
 *
 * Urgent is not the same on every version. `SYNC_EXTRAS_EXPEDITED` only puts the sync first in
 * the queue: the job still waits for the sync adapter's standby bucket, and a request made from
 * the background (an answer given from a notification button) gets no exemption from it, so the
 * reply can wait for hours. From Android 12 the sync can run as an expedited job instead, which
 * the bucket does not hold back. The two extras cannot go together; should the system refuse the
 * expedited job, the request is made again the old way.
 */
class ContentResolverSyncTrigger internal constructor(
    private val sdk: Int,
    private val system: (CalendarAccount, Set<String>) -> Unit
) : AccountSyncTrigger {
    @Inject
    constructor() : this(Build.VERSION.SDK_INT, ::requestFromSystem)

    override fun request(account: CalendarAccount, expedited: Boolean) {
        if (!expedited) return system(account, emptySet())
        if (sdk >= Build.VERSION_CODES.S) {
            try {
                return system(account, AS_EXPEDITED_JOB)
            } catch (_: IllegalArgumentException) {
                // Refused as an expedited job: asked again below, first in the queue.
            }
        }
        system(account, FIRST_IN_QUEUE)
    }

    internal companion object {
        /** `ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB`, added in API 31. */
        private const val SCHEDULE_AS_EXPEDITED_JOB = "schedule_as_expedited_job"

        val AS_EXPEDITED_JOB =
            setOf(ContentResolver.SYNC_EXTRAS_MANUAL, SCHEDULE_AS_EXPEDITED_JOB)
        val FIRST_IN_QUEUE =
            setOf(ContentResolver.SYNC_EXTRAS_MANUAL, ContentResolver.SYNC_EXTRAS_EXPEDITED)
    }
}

private fun requestFromSystem(account: CalendarAccount, flags: Set<String>) {
    val extras = Bundle().apply { flags.forEach { putBoolean(it, true) } }
    ContentResolver.requestSync(
        Account(account.name, account.type),
        CalendarContract.AUTHORITY,
        extras
    )
}
