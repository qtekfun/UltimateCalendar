// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The CalDAV account whose data the source shows: the one signed in, once the sync has stored
 * it. Nobody signed in, or no sync done yet, means no CalDAV calendars at all, whatever rows an
 * earlier account left. Blocking: call it off the main thread.
 */
@Singleton
class CalDavAccounts @Inject constructor(
    private val session: AccountSession,
    private val database: UltimateCalendarDatabase
) {
    suspend fun current(): DavAccountEntity? {
        // A background process may start before the UI loaded the login.
        val signedIn = session.activeAccount.value ?: session.restore() ?: return null
        return database.davAccountDao().find(signedIn.serverUrl, signedIn.loginName)
    }
}

/** Asks for a sync soon after a local change; changes are queued, so a late sync loses nothing. */
fun interface CalDavSyncTrigger {
    /**
     * A change was stored. With [promptly] (an invitation or an answer: the server sends the mail
     * when it receives the change) the sync starts after a couple of seconds, not after the
     * usual wait that coalesces ordinary edits.
     */
    fun localChange(promptly: Boolean)
}

/** Makes the UID of a new event, which also names its resource on the server. */
fun interface UidFactory {
    fun next(): String
}

/** Random UIDs, with the app's domain so that they are recognizable on the server. */
class RandomUidFactory @Inject constructor() : UidFactory {
    override fun next(): String = "${UUID.randomUUID()}@ultimatecalendar"
}
