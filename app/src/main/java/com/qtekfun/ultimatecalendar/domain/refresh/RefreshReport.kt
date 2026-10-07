// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.refresh

/** Why part of a refresh the user asked for did not happen or did not work. */
enum class RefreshIssue {
    /** There is no network: only what is on the phone was read again. */
    OFFLINE,

    /** The calendar permission is missing, so the phone's own calendars cannot be read. */
    PERMISSION_MISSING,

    /** An account of the phone cannot sync calendars (its sync is off or it has no adapter). */
    ACCOUNT_SYNC_OFF,

    /** A server did not answer, answered an error, or took too long. */
    SERVER_ERROR,

    /** The CalDAV server no longer accepts the app password. */
    SIGN_IN_REFUSED,

    /** The calendars could not be read from the phone. */
    READ_FAILED
}

/**
 * How a refresh the user asked for went: nothing wrong is [upToDate]; otherwise [issues] says
 * which parts failed, always in the same order. The screen words it.
 */
data class RefreshReport(val issues: Set<RefreshIssue> = emptySet()) {
    val upToDate: Boolean get() = issues.isEmpty()

    /** The issues in a stable order, the most useful first. */
    val ordered: List<RefreshIssue> get() = issues.sorted()
}
