// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.settings.backup.RestoreOutcome
import com.qtekfun.ultimatecalendar.data.settings.backup.RestoreResult
import com.qtekfun.ultimatecalendar.data.settings.backup.SessionRestoreResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupMessagesTest {
    private fun text(id: Int) = BackupMessage.Text(id)

    @Test
    fun `each result of the settings has its message`() {
        assertEquals(
            listOf(text(R.string.backup_restored)),
            restoreMessages(RestoreOutcome(RestoreResult.Restored()))
        )
        assertEquals(
            listOf(text(R.string.backup_restored_some_calendars)),
            restoreMessages(RestoreOutcome(RestoreResult.Restored(), missingCalendars = 2))
        )
        assertEquals(
            listOf(text(R.string.backup_wrong_passphrase)),
            restoreMessages(RestoreOutcome(RestoreResult.WrongPassphrase))
        )
        assertEquals(
            listOf(text(R.string.backup_invalid)),
            restoreMessages(RestoreOutcome(RestoreResult.Invalid))
        )
        assertEquals(
            listOf(text(R.string.backup_newer_version)),
            restoreMessages(RestoreOutcome(RestoreResult.NewerVersion))
        )
    }

    @Test
    fun `a CalDAV sign-in adds its own message after the settings one`() {
        val account = SignedInAccount("https://cloud.example.com/", "ana")
        val expected = mapOf(
            SessionRestoreResult.SignedIn(account) to R.string.backup_session_restored,
            SessionRestoreResult.Rejected to R.string.backup_session_rejected,
            SessionRestoreResult.Unreachable to R.string.backup_session_unreachable,
            SessionRestoreResult.Invalid to R.string.backup_session_invalid,
            SessionRestoreResult.AlreadySignedIn to R.string.backup_session_already
        )

        expected.forEach { (result, message) ->
            assertEquals(
                listOf(text(R.string.backup_restored), text(message)),
                restoreMessages(RestoreOutcome(RestoreResult.Restored(), 0, result))
            )
        }
    }

    @Test
    fun `calendars waiting for the first sync are counted, not called missing`() {
        assertEquals(
            listOf(
                text(R.string.backup_restored),
                BackupMessage.Count(R.plurals.backup_restored_waiting_calendars, 3)
            ),
            restoreMessages(RestoreOutcome(RestoreResult.Restored(), waitingCalendars = 3))
        )
    }

    @Test
    fun `missing and waiting calendars each get their message, the sign-in last`() {
        assertEquals(
            listOf(
                text(R.string.backup_restored_some_calendars),
                BackupMessage.Count(R.plurals.backup_restored_waiting_calendars, 1),
                text(R.string.backup_session_restored)
            ),
            restoreMessages(
                RestoreOutcome(
                    RestoreResult.Restored(),
                    missingCalendars = 2,
                    session = SessionRestoreResult.SignedIn(SignedInAccount("https://x/", "ana")),
                    waitingCalendars = 1
                )
            )
        )
    }

    @Test
    fun `nothing is said about waiting calendars when the restore failed`() {
        assertEquals(
            listOf(text(R.string.backup_invalid)),
            restoreMessages(RestoreOutcome(RestoreResult.Invalid, waitingCalendars = 1))
        )
    }
}
