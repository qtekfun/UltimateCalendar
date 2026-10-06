// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

/** DURATION values (RFC 5545 §3.3.6), such as `-PT15M` or `P1W`, as signed seconds. */
object IcsDuration {
    private val PATTERN =
        Regex("""([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")

    fun parse(text: String): Long? {
        val parts = PATTERN.matchEntire(text.trim().uppercase())?.groupValues
        val units = parts?.drop(2)?.takeIf { values -> values.any { it.isNotEmpty() } }
        return units?.let {
            val total = it.zip(UNITS).sumOf { (value, unit) -> value.number() * unit }
            if (parts[1] == "-") -total else total
        }
    }

    fun format(seconds: Long): String {
        val sign = if (seconds < 0) "-" else ""
        val total = Math.abs(seconds)
        val days = total / DAY
        val rest = total % DAY
        val time = buildString {
            if (rest / HOUR > 0) append(rest / HOUR).append('H')
            if (rest % HOUR / MINUTE > 0) append(rest % HOUR / MINUTE).append('M')
            if (rest % MINUTE > 0) append(rest % MINUTE).append('S')
        }
        return when {
            total == 0L -> "PT0S"

            total % WEEK == 0L -> "${sign}P${total / WEEK}W"

            else -> sign + "P" + (if (days > 0) "${days}D" else "") +
                (if (time.isNotEmpty()) "T$time" else "")
        }
    }

    private fun String.number(): Long = toLongOrNull() ?: 0

    private const val MINUTE = 60L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY
    private val UNITS = listOf(WEEK, DAY, HOUR, MINUTE, 1L)
}
