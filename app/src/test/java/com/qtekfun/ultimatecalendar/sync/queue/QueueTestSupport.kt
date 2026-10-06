// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.queue

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/** A clock tests move by hand; its zone is the one floating times are read in. */
class MutableClock(
    var now: Instant = Instant.parse("2026-10-01T10:00:00Z"),
    private val zone: ZoneId = ZoneOffset.UTC
) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId?): Clock = MutableClock(now, requireNotNull(zone))

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/** A Random whose nextDouble is fixed: 0.5 means no jitter, 0 and 1 are the extremes. */
class FixedRandom(private val value: Double) : Random() {
    override fun nextBits(bitCount: Int): Int = 0

    override fun nextDouble(): Double = value
}

/** Records every call and answers from a script per event; unscripted calls succeed. */
class ScriptedExecutor : OperationExecutor {
    val calls = mutableListOf<Pair<Long, QueuedOperation>>()
    private val scripts = mutableMapOf<Long, ArrayDeque<() -> ExecutionResult>>()

    fun on(eventId: Long, vararg results: () -> ExecutionResult) {
        scripts.getOrPut(eventId) { ArrayDeque() }.addAll(results)
    }

    /** Whether each call was told the operation may already have been sent. */
    val maybeSent = mutableListOf<Boolean>()

    override suspend fun execute(
        eventId: Long,
        operation: QueuedOperation,
        maybeSent: Boolean
    ): ExecutionResult {
        calls += eventId to operation
        this.maybeSent += maybeSent
        return scripts[eventId]?.removeFirstOrNull()?.invoke() ?: ExecutionResult.Done
    }
}
