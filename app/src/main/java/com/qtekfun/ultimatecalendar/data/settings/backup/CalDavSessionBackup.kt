// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDavProvider
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavResult
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * The CalDAV sign-in as a backup keeps it (RF-11, RF-12). It holds the app password, so it only
 * ever exists inside the sealed content of a backup (AES-GCM under the user's passphrase) and in
 * memory while restoring: [toString] never shows the password.
 */
@Serializable
class BackupSession(val serverUrl: String, val loginName: String, val appPassword: String) {
    override fun toString(): String = "BackupSession(loginName=$loginName, appPassword=***)"

    override fun equals(other: Any?): Boolean = other is BackupSession &&
        serverUrl == other.serverUrl &&
        loginName == other.loginName &&
        appPassword == other.appPassword

    override fun hashCode(): Int = listOf(serverUrl, loginName, appPassword).hashCode()
}

/** What came of signing in again from a restored backup. */
sealed interface SessionRestoreResult {
    /** The server accepted the stored app password and the account is signed in again. */
    data class SignedIn(val account: SignedInAccount) : SessionRestoreResult

    /** The server refuses the credential (revoked, or the account is gone): sign in again. */
    data object Rejected : SessionRestoreResult

    /** The server could not be reached or answered something unusable; try again later. */
    data object Unreachable : SessionRestoreResult

    /** The stored server address is not a valid HTTPS address. */
    data object Invalid : SessionRestoreResult

    /** Somebody is signed in already: the backup's account is left alone. */
    data object AlreadySignedIn : SessionRestoreResult
}

/**
 * Takes the CalDAV sign-in out of this phone for a backup, and signs in again from one on
 * another. Restoring never trusts the stored password blindly: it is checked against the server
 * first, and only a credential the server accepts reaches the Keystore-encrypted store (through
 * [AccountSession.signIn]); a rejected or unreachable one is dropped from memory, never stored.
 */
class CalDavSessionBackup @Inject constructor(
    private val session: AccountSession,
    private val provider: CalDavProvider,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /** The signed-in account with its app password, or null when nobody is signed in. */
    suspend fun collect(): BackupSession? = withContext(io) {
        val account = session.activeAccount.value ?: session.restore() ?: return@withContext null
        session.credentials()?.let {
            BackupSession(account.serverUrl, it.loginName, it.appPassword)
        }
    }

    suspend fun restore(backup: BackupSession): SessionRestoreResult =
        restore(backup, allowInsecure = false)

    /** [allowInsecure] exists only so tests can use a local plain-http server. */
    internal suspend fun restore(
        backup: BackupSession,
        allowInsecure: Boolean
    ): SessionRestoreResult = withContext(io) {
        val server =
            (ServerUrl.parse(backup.serverUrl, allowInsecure) as? ServerUrl.ParseResult.Valid)?.url
        val login = Credentials(backup.loginName, backup.appPassword)
        val account = SignedInAccount(backup.serverUrl, backup.loginName)
        val dav = server?.let { provider.connectWith(account, login, allowInsecure) }
        if (server == null || dav == null) {
            SessionRestoreResult.Invalid
        } else if ((session.activeAccount.value ?: session.restore()) != null) {
            SessionRestoreResult.AlreadySignedIn
        } else {
            when (dav.read.discover()) {
                is DavResult.Success -> SessionRestoreResult.SignedIn(session.signIn(server, login))
                DavResult.Unauthorized, DavResult.Forbidden -> SessionRestoreResult.Rejected
                else -> SessionRestoreResult.Unreachable
            }
        }
    }
}
