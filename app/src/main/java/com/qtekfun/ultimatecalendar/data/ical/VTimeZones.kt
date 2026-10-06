// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneOffsetTransitionRule

/**
 * Builds the `VTIMEZONE` that RFC 5545 requires for every TZID used in a file, from the
 * rules java.time knows today: one STANDARD block, plus a DAYLIGHT one for zones with
 * summer time. Enough for clients to read local times; history is not needed for tasks.
 */
object VTimeZones {
    private val LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private const val EPOCH_YEAR = 1970

    fun of(zoneId: String): IcsComponent? {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return null
        val rules = zone.rules
        val transitions = rules.transitionRules
        val blocks = if (transitions.size == 2) {
            transitions.map { block(it) }
        } else {
            val offset = rules.getStandardOffset(java.time.Instant.now())
            listOf(fixed(offset))
        }
        return IcsComponent(
            "VTIMEZONE",
            listOf(IcsProperty("TZID", value = zoneId)) + blocks
        )
    }

    private fun fixed(offset: ZoneOffset) = IcsComponent(
        "STANDARD",
        listOf(
            IcsProperty("DTSTART", value = LOCAL.format(LocalDateTime.of(EPOCH_YEAR, 1, 1, 0, 0))),
            IcsProperty("TZOFFSETFROM", value = offsetText(offset)),
            IcsProperty("TZOFFSETTO", value = offsetText(offset))
        )
    )

    private fun block(rule: ZoneOffsetTransitionRule): IcsComponent {
        val transition = rule.createTransition(EPOCH_YEAR)
        val daylight = rule.offsetAfter.totalSeconds > rule.standardOffset.totalSeconds
        return IcsComponent(
            if (daylight) "DAYLIGHT" else "STANDARD",
            listOf(
                IcsProperty("DTSTART", value = LOCAL.format(transition.dateTimeBefore)),
                IcsProperty("RRULE", value = recurrence(rule)),
                IcsProperty("TZOFFSETFROM", value = offsetText(rule.offsetBefore)),
                IcsProperty("TZOFFSETTO", value = offsetText(rule.offsetAfter))
            )
        )
    }

    /**
     * java.time describes the change day as "weekday on or after day N" (or "last day"). The
     * last weekday of the month, the n-th weekday and any other week of days map to RRULE.
     */
    private fun recurrence(rule: ZoneOffsetTransitionRule): String {
        val day = rule.dayOfWeek?.name?.take(2)
        val indicator = rule.dayOfMonthIndicator
        val inLastWeek = indicator < 0 || indicator > rule.month.minLength() - DAYS_IN_WEEK
        val byDay = when {
            day == null -> "BYMONTHDAY=$indicator"

            inLastWeek -> "BYDAY=-1$day"

            (indicator - 1) % DAYS_IN_WEEK == 0 -> "BYDAY=${(indicator - 1) / DAYS_IN_WEEK + 1}$day"

            else -> "BYMONTHDAY=${(indicator until indicator + DAYS_IN_WEEK).joinToString(
                ","
            )};BYDAY=$day"
        }
        return "FREQ=YEARLY;BYMONTH=${rule.month.value};$byDay"
    }

    private fun offsetText(offset: ZoneOffset): String {
        val total = offset.totalSeconds
        val sign = if (total < 0) "-" else "+"
        val minutes = Math.abs(total) / SECONDS_PER_MINUTE
        return sign + "%02d%02d".format(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }

    private const val DAYS_IN_WEEK = 7
    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60
}
