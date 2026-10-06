// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.perf

import org.junit.jupiter.api.Assertions.assertTrue

/** Volumes of SPEC §2: the reference one (5,000 events) and the stress one (20,000). */
internal val VOLUMES = intArrayOf(5_000, 20_000)

/**
 * Times [block]: a few warm-up runs, then the best and the median of [runs] runs, printed as one
 * `BENCH` line so the numbers can be read in the test log. Returns the best time in milliseconds,
 * the figure least disturbed by a loaded machine.
 */
internal fun <T> bench(label: String, runs: Int = 7, block: () -> T): Double {
    repeat(WARM_UP) { block() }
    val times = DoubleArray(runs) {
        val started = System.nanoTime()
        block()
        (System.nanoTime() - started) / NANOS_PER_MS
    }.sorted()
    println(
        "BENCH %-52s best=%9.2f ms  median=%9.2f ms".format(label, times.first(), times[runs / 2])
    )
    return times.first()
}

/**
 * Fails when [bestMs] exceeds [limitMs]. The limits are set one or two orders of magnitude over
 * what a normal machine measures: they catch a complexity regression, not a slow runner.
 */
internal fun assertBelow(label: String, bestMs: Double, limitMs: Double) {
    assertTrue(bestMs < limitMs) { "$label took $bestMs ms, over the limit of $limitMs ms" }
}

private const val WARM_UP = 2
private const val NANOS_PER_MS = 1_000_000.0
