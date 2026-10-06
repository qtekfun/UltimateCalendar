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
    @Test
    fun `each result of the settings has its message`() {
        assertEquals(
            listOf(R.string.backup_restored),
            restoreMessages(RestoreOutcome(RestoreResult.Restored()))
        )
        assertEquals(
            listOf(R.string.backup_restored_some_calendars),
            restoreMessages(RestoreOutcome(RestoreResult.Restored(), missingCalendars = 2))
        )
        assertEquals(
            listOf(R.string.backup_wrong_passphrase),
            restoreMessages(RestoreOutcome(RestoreResult.WrongPassphrase))
        )
        assertEquals(
            listOf(R.string.backup_invalid),
            restoreMessages(RestoreOutcome(RestoreResult.Invalid))
        )
        assertEquals(
            listOf(R.string.backup_newer_version),
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
                listOf(R.string.backup_restored, message),
                restoreMessages(RestoreOutcome(RestoreResult.Restored(), 0, result))
            )
        }
    }
}
