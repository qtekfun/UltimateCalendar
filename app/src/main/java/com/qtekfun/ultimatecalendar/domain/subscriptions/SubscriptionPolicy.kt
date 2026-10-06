// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.subscriptions

import java.time.Duration
import java.time.Instant

/** What the periodic refresh work should be. */
sealed interface SchedulePlan {
    /** Nothing to refresh by itself: no work at all, so nothing wakes up for nothing. */
    data object None : SchedulePlan

    /** One periodic work, every [hours] hours. */
    data class Every(val hours: Int) : SchedulePlan
}

/**
 * The rules of when subscriptions are downloaded: pure decisions, the work and the clock are the
 * caller's.
 */
object SubscriptionPolicy {
    const val MAX_NAME_LENGTH = 80

    private const val HTTP_SERVER_ERROR = 500
    private const val HTTP_TOO_MANY = 429

    /** WorkManager is not exact: a run a little before the interval counts as the one due. */
    private val GRACE: Duration = Duration.ofMinutes(30)

    /** Whether [subscription] is due for its automatic refresh at [now]. */
    fun isDue(subscription: Subscription, now: Instant): Boolean {
        if (!subscription.automatic) return false
        val last = subscription.lastAttemptAt ?: return true
        val due = last.plus(Duration.ofHours(subscription.interval.hours.toLong())).minus(GRACE)
        return !now.isBefore(due)
    }

    /**
     * The subscriptions a periodic run refreshes. A run that is a retry after a failure also takes
     * the ones that failed the last time, so a temporary problem is tried again with backoff
     * instead of waiting a whole interval.
     */
    fun toRefresh(
        subscriptions: List<Subscription>,
        now: Instant,
        retrying: Boolean
    ): List<Subscription> = subscriptions.filter {
        isDue(it, now) || (retrying && it.automatic && it.failing)
    }

    /** The work to keep: one periodic run at the shortest interval of the automatic ones. */
    fun plan(subscriptions: List<Subscription>): SchedulePlan {
        val shortest = subscriptions.filter { it.automatic }.minOfOrNull { it.interval.hours }
        return if (shortest == null) SchedulePlan.None else SchedulePlan.Every(shortest)
    }

    /** Whether a failure is worth another try soon: the network or the server, not the address. */
    fun isTemporary(error: SubscriptionError, httpCode: Int?): Boolean = when (error) {
        SubscriptionError.TIMEOUT, SubscriptionError.UNREACHABLE, SubscriptionError.NETWORK -> true

        SubscriptionError.HTTP ->
            httpCode == null || httpCode >= HTTP_SERVER_ERROR || httpCode == HTTP_TOO_MANY

        else -> false
    }

    /** A name the user typed, trimmed and limited; a blank one becomes [fallback] (the host). */
    fun cleanName(name: String, fallback: String): String =
        name.trim().take(MAX_NAME_LENGTH).trim().ifEmpty { fallback }

    private val Subscription.automatic: Boolean
        get() = enabled && interval != RefreshInterval.MANUAL
}
