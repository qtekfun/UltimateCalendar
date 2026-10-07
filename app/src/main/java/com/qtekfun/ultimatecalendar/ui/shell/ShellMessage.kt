// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import android.content.res.Resources
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.refresh.RefreshIssue
import com.qtekfun.ultimatecalendar.domain.refresh.RefreshReport

/** What the shell tells the user in a snackbar about something that happened in the background. */
sealed interface ShellMessage {
    /** A refresh the user asked for ended. */
    data class Refreshed(val report: RefreshReport) : ShellMessage

    /** An event with guests was saved but its account could not be asked to sync now. */
    data object InvitationsLater : ShellMessage
}

/** The words of a [ShellMessage]. */
internal fun ShellMessage.text(resources: Resources): String = when (this) {
    is ShellMessage.Refreshed ->
        if (report.upToDate) {
            resources.getString(R.string.refresh_up_to_date)
        } else {
            resources.getString(
                R.string.refresh_issues,
                report.ordered.joinToString(", ") { resources.getString(it.label()) }
            )
        }

    ShellMessage.InvitationsLater -> resources.getString(R.string.invitations_sent_when_synced)
}

private fun RefreshIssue.label(): Int = when (this) {
    RefreshIssue.OFFLINE -> R.string.refresh_issue_offline
    RefreshIssue.PERMISSION_MISSING -> R.string.refresh_issue_permission
    RefreshIssue.ACCOUNT_SYNC_OFF -> R.string.refresh_issue_account_sync
    RefreshIssue.SERVER_ERROR -> R.string.refresh_issue_server
    RefreshIssue.SIGN_IN_REFUSED -> R.string.refresh_issue_sign_in
    RefreshIssue.READ_FAILED -> R.string.refresh_issue_read
}
