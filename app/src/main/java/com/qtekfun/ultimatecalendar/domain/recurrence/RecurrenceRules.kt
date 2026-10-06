// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Reads and writes RRULE values. */
object RecurrenceRules {
    private val DAYS = mapOf(
        "MO" to DayOfWeek.MONDAY,
        "TU" to DayOfWeek.TUESDAY,
        "WE" to DayOfWeek.WEDNESDAY,
        "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY,
        "SA" to DayOfWeek.SATURDAY,
        "SU" to DayOfWeek.SUNDAY
    )
    private val BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE
    private const val PARTS = "FREQ INTERVAL BYDAY BYMONTHDAY BYMONTH BYSETPOS COUNT UNTIL WKST"
    private val KNOWN = PARTS.split(' ').toSet()
    private val WEEKDAY = Regex("([+-]?\\d{1,2})?(MO|TU|WE|TH|FR|SA|SU)")
    private val BASIC_MOMENT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private const val DATE_LENGTH = 8

    /** The rule, or null when it uses parts the app does not handle. */
    fun parse(text: String): RecurrenceRule? = runCatching {
        val parts = text.trim().removePrefix("RRULE:").split(';').filter {
            it.isNotBlank()
        }.associate {
            val (key, value) = it.split('=', limit = 2)
            key.uppercase() to value.uppercase()
        }
        require(parts.keys.all { it in KNOWN })
        RecurrenceRule(
            frequency = Frequency.valueOf(parts.getValue("FREQ")),
            interval = parts["INTERVAL"]?.toInt()?.also { require(it > 0) } ?: 1,
            byDay = parts["BYDAY"]?.let { days -> days.split(',').map(::weekday) }.orEmpty(),
            byMonthDay = numbers(parts["BYMONTHDAY"]),
            byMonth = numbers(parts["BYMONTH"]),
            bySetPos = numbers(parts["BYSETPOS"]),
            count = parts["COUNT"]?.toInt()?.also { require(it > 0) },
            until = parts["UNTIL"]?.let(::parseUntil),
            weekStart = weekStart(parts["WKST"])
        )
    }.getOrNull()

    fun format(rule: RecurrenceRule): String = buildList {
        add("FREQ=${rule.frequency}")
        if (rule.interval != 1) add("INTERVAL=${rule.interval}")
        if (rule.byDay.isNotEmpty()) {
            add(
                "BYDAY=" +
                    rule.byDay.joinToString(",") { it.ordinal?.toString().orEmpty() + code(it.day) }
            )
        }
        if (rule.byMonthDay.isNotEmpty()) add("BYMONTHDAY=" + rule.byMonthDay.joinToString(","))
        if (rule.byMonth.isNotEmpty()) add("BYMONTH=" + rule.byMonth.joinToString(","))
        if (rule.bySetPos.isNotEmpty()) add("BYSETPOS=" + rule.bySetPos.joinToString(","))
        rule.count?.let { add("COUNT=$it") }
        rule.until?.let { add("UNTIL=" + formatUntil(it)) }
        if (rule.weekStart != DayOfWeek.MONDAY) add("WKST=" + code(rule.weekStart))
    }.joinToString(";")

    /** `20270131` is a day; `20270131T225959Z` a UTC moment; a floating time counts as its day. */
    private fun parseUntil(text: String): Until = if (text.endsWith('Z')) {
        Until.Moment(LocalDateTime.parse(text, BASIC_MOMENT).toInstant(ZoneOffset.UTC))
    } else {
        Until.Day(LocalDate.parse(text.take(DATE_LENGTH), BASIC_DATE))
    }

    private fun formatUntil(until: Until): String = when (until) {
        is Until.Day -> BASIC_DATE.format(until.date)
        is Until.Moment -> BASIC_MOMENT.format(until.at.atOffset(ZoneOffset.UTC))
    }

    private fun weekStart(code: String?): DayOfWeek = if (code ==
        null
    ) {
        DayOfWeek.MONDAY
    } else {
        DAYS.getValue(code)
    }

    private fun code(day: DayOfWeek) = DAYS.entries.first { it.value == day }.key

    private fun weekday(text: String): WeekdayNum {
        val match = requireNotNull(WEEKDAY.matchEntire(text))
        val ordinal = match.groupValues[1].takeIf { it.isNotEmpty() }?.toInt()
        require(ordinal != 0)
        return WeekdayNum(DAYS.getValue(match.groupValues[2]), ordinal)
    }

    private fun numbers(text: String?): List<Int> = text?.let { list ->
        list.split(',').map { it.toInt().also { n -> require(n != 0) } }
    }.orEmpty()
}
