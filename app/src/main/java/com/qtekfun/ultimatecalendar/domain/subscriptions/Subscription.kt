// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.subscriptions

import java.time.Instant

/** How often a subscription is downloaded again; [MANUAL] only when the user asks. */
enum class RefreshInterval(val hours: Int) {
    MANUAL(0),
    EVERY_6_HOURS(6),
    EVERY_12_HOURS(12),
    EVERY_24_HOURS(24);

    companion object {
        val DEFAULT = EVERY_12_HOURS

        /** The interval of [hours], or null when there is none (a backup from a newer app). */
        fun ofHours(hours: Int): RefreshInterval? = entries.firstOrNull { it.hours == hours }
    }
}

/** Why the last refresh of a subscription failed. Never carries the address. */
enum class SubscriptionError {
    TIMEOUT,
    UNREACHABLE,
    TLS,

    /** The server answered with an error status; the code goes with it. */
    HTTP,
    TOO_LARGE,

    /** A redirect pointed to plain http (or to something that is not a web address). */
    INSECURE_REDIRECT,
    TOO_MANY_REDIRECTS,

    /** What came back is not a calendar. */
    NOT_CALENDAR,
    NETWORK,

    /** The stored address cannot be decrypted any more (the Keystore key is gone). */
    UNREADABLE_URL
}

/**
 * A calendar the user subscribed to by address (T39). The address is a secret (many feeds put a
 * token in it), so it is not here: only its [host], which is safe to show.
 */
data class Subscription(
    val id: Long,
    val name: String,
    /** ARGB. */
    val color: Int,
    val enabled: Boolean,
    val interval: RefreshInterval,
    val host: String,
    val lastAttemptAt: Instant?,
    val lastSuccessAt: Instant?,
    val error: SubscriptionError?,
    val errorCode: Int?,
    /** Events read in the last download. */
    val eventCount: Int,
    /** Events of the last download that could not be read. */
    val skipped: Int
) {
    /** Failed the last time it was tried: its events, if any, are the ones of an earlier success. */
    val failing: Boolean get() = error != null
}
