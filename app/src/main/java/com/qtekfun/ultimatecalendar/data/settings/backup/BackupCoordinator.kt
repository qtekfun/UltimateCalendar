// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavMapping
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionRepository
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * How a restore went as a whole: the settings result, how many calendars could not be applied
 * ([missingCalendars]: overrides that found no calendar, and subscriptions refused for not being
 * https) and, if the backup carried a CalDAV sign-in, what came of signing in again ([session],
 * null when it carried none). [waitingCalendars] counts the overrides of CalDAV calendars that
 * do not exist yet and will be applied by the account's first sync. A session that fails never stops the rest of the restore.
 */
data class RestoreOutcome(
    val result: RestoreResult,
    val missingCalendars: Int = 0,
    val session: SessionRestoreResult? = null,
    val waitingCalendars: Int = 0
)

/**
 * Puts together what a backup holds (RF-11, RF-12): the settings, the calendar overrides, the
 * subscriptions and, if the user asks, the CalDAV sign-in; and takes it apart again on restore.
 * The sign-in is only read from the Keystore-backed session when [export] is asked to include it,
 * and on restore it reaches the Keystore only after the server accepted it.
 */
class BackupCoordinator @Inject constructor(
    private val backup: SettingsBackup,
    private val overrides: CalendarOverridesBackup,
    private val sessions: CalDavSessionBackup,
    private val subscriptions: SubscriptionRepository,
    private val accounts: AccountSession,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /** The backup file as text. [includeSession] adds the CalDAV sign-in, if there is one. */
    suspend fun export(passphrase: CharArray, includeSession: Boolean): String {
        val calendars = overrides.collect()
        val feeds = subscriptions.forBackup()
        val session = if (includeSession) sessions.collect() else null
        return withContext(io) { backup.export(passphrase, calendars, feeds, session) }
    }

    suspend fun restore(text: String, passphrase: CharArray): RestoreOutcome {
        val result = withContext(io) { backup.restore(text, passphrase) }
        val restored = result as? RestoreResult.Restored ?: return RestoreOutcome(result)
        // Signed in first: the account's calendars are not there until its first sync, so the
        // overrides of its CalDAV calendars find no match yet and wait for that sync.
        val session = restored.session?.let { sessions.restore(it) }
        // Subscriptions come first: their calendars may be named by the overrides.
        val refused = subscriptions.restore(restored.subscriptions).refused
        val calDav = accounts.activeAccount.value?.let {
            CalDavMapping.accountName(it.serverUrl, it.loginName)
        }
        val applied = overrides.apply(restored.calendars, calDav)
        return RestoreOutcome(result, applied.missing + refused, session, applied.waiting)
    }
}
