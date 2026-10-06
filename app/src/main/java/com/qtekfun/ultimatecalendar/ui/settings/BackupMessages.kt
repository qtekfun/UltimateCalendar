// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import androidx.annotation.StringRes
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.settings.backup.RestoreOutcome
import com.qtekfun.ultimatecalendar.data.settings.backup.RestoreResult
import com.qtekfun.ultimatecalendar.data.settings.backup.SessionRestoreResult

/** What to tell the user after a restore: one message, and one more if a CalDAV sign-in came along. */
internal fun restoreMessages(outcome: RestoreOutcome): List<Int> = listOfNotNull(
    when (outcome.result) {
        is RestoreResult.Restored ->
            if (outcome.missingCalendars > 0) {
                R.string.backup_restored_some_calendars
            } else {
                R.string.backup_restored
            }

        RestoreResult.WrongPassphrase -> R.string.backup_wrong_passphrase

        RestoreResult.Invalid -> R.string.backup_invalid

        RestoreResult.NewerVersion -> R.string.backup_newer_version
    },
    outcome.session?.let(::sessionMessage)
)

@StringRes
internal fun sessionMessage(result: SessionRestoreResult): Int = when (result) {
    is SessionRestoreResult.SignedIn -> R.string.backup_session_restored
    SessionRestoreResult.Rejected -> R.string.backup_session_rejected
    SessionRestoreResult.Unreachable -> R.string.backup_session_unreachable
    SessionRestoreResult.Invalid -> R.string.backup_session_invalid
    SessionRestoreResult.AlreadySignedIn -> R.string.backup_session_already
}
