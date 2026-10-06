// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.queue

import java.time.Duration
import kotlin.random.Random

private const val BASE_DELAY_SECONDS = 5L
private val BASE_DELAY: Duration = Duration.ofSeconds(BASE_DELAY_SECONDS)
private val MAX_DELAY: Duration = Duration.ofHours(1)
private const val MAX_DOUBLINGS = 20
private const val JITTER = 0.2

/** Exponential backoff: 5 s, 10 s, 20 s… up to 1 h, spread by ±20 % so devices do not retry in step. */
class RetryBackoff(private val random: Random) {
    fun delay(attempts: Int): Duration {
        val exponential = BASE_DELAY.multipliedBy(1L shl attempts.coerceAtMost(MAX_DOUBLINGS))
        val capped = minOf(exponential, MAX_DELAY)
        val jitter = 1 + (random.nextDouble() * 2 - 1) * JITTER
        return Duration.ofMillis((capped.toMillis() * jitter).toLong())
    }
}
