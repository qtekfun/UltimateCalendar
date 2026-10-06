// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

import java.time.Duration
import java.time.Instant

/** How the test reminder went: it travels the same way as real ones, a minute later. */
sealed interface TestDelivery {
    val scheduledAt: Instant

    data class Waiting(override val scheduledAt: Instant) : TestDelivery

    data class OnTime(override val scheduledAt: Instant, val arrivedAt: Instant) : TestDelivery

    data class Late(override val scheduledAt: Instant, val arrivedAt: Instant, val minutes: Long) :
        TestDelivery

    /** Long past its time and still not here: the phone is holding reminders back. */
    data class Missing(override val scheduledAt: Instant) : TestDelivery

    companion object {
        /** An alarm this close to its time counts as on time. */
        val TOLERANCE: Duration = Duration.ofMinutes(1)

        /** After this, waiting longer means the phone is not delivering it. */
        val GIVE_UP: Duration = Duration.ofMinutes(10)

        fun of(scheduledAt: Instant?, arrivedAt: Instant?, now: Instant): TestDelivery? = when {
            scheduledAt == null -> null

            arrivedAt != null -> {
                val delay = Duration.between(scheduledAt, arrivedAt)
                if (delay <= TOLERANCE) {
                    OnTime(scheduledAt, arrivedAt)
                } else {
                    Late(scheduledAt, arrivedAt, delay.toMinutes())
                }
            }

            now.isAfter(scheduledAt.plus(GIVE_UP)) -> Missing(scheduledAt)

            else -> Waiting(scheduledAt)
        }
    }
}
