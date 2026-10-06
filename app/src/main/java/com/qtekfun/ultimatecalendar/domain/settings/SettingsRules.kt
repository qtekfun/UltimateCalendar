// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.settings

import java.time.LocalTime
import java.util.Locale

/** Spans of time, in minutes, that the choices are made of. */
private object Span {
    const val M5 = 5
    const val M10 = 10
    const val M15 = 15
    const val M30 = 30
    const val M45 = 45
    const val HOUR = 60
    const val H1_5 = 90
    const val H2 = 120
    const val DAY = 24 * HOUR
    const val DAYS_2 = 2 * DAY
    const val WEEK = 7 * DAY
    const val WEEKS_4 = 4 * WEEK
    const val H6 = 6
    const val H24 = 24
    const val H48 = 48
    const val ALL_DAY_DEFAULT = 9 * HOUR
}

/** What the settings accept (RF-10): the choices offered, and how any other value is mended. */
object SettingsRules {
    const val DEFAULT_DURATION_MINUTES = Span.HOUR
    const val MIN_DURATION_MINUTES = Span.M5
    const val MAX_DURATION_MINUTES = Span.DAY
    val DURATION_CHOICES =
        listOf(Span.M15, Span.M30, Span.M45, Span.HOUR, Span.H1_5, Span.H2)

    const val DEFAULT_MISSED_WINDOW_HOURS = Span.H24

    /** Hours a missed reminder is brought back for; 0 is never. */
    val MISSED_WINDOW_CHOICES = listOf(Span.H6, Span.H24, Span.H48, 0)

    const val DEFAULT_ALL_DAY_MINUTE = Span.ALL_DAY_DEFAULT

    /** Half-hours of the day, as minutes after midnight, offered for the all-day reminder. */
    val ALL_DAY_TIME_CHOICES = (0 until Span.DAY step Span.M30).toList()

    const val MAX_REMINDER_MINUTES = Span.WEEKS_4
    const val MAX_REMINDERS = 5
    val DEFAULT_REMINDERS = listOf(Span.M10)
    val DEFAULT_ALL_DAY_REMINDERS = listOf(0)

    /** Offers for a new reminder, in minutes before the event. */
    val REMINDER_CHOICES = listOf(
        0, Span.M5, Span.M10, Span.M15, Span.M30, Span.HOUR, Span.H2, Span.DAY, Span.DAYS_2,
        Span.WEEK
    )

    private const val SECONDS_PER_MINUTE = 60L
    const val MAX_ALIASES = 20
    const val MIN_PASSPHRASE_LENGTH = 8
    private val EMAIL = Regex("[^@\\s,;<>]+@[^@\\s,;<>]+\\.[^@\\s,;<>]+")

    fun duration(minutes: Int): Int = minutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)

    /** Only the offered windows exist; anything else (an older or hand-made value) is the default. */
    fun missedWindow(hours: Int): Int =
        if (hours in MISSED_WINDOW_CHOICES) hours else DEFAULT_MISSED_WINDOW_HOURS

    fun allDayMinute(minute: Int): Int = minute.coerceIn(0, Span.DAY - 1)

    fun allDayTime(minute: Int): LocalTime =
        LocalTime.ofSecondOfDay(allDayMinute(minute) * SECONDS_PER_MINUTE)

    /** Reminders as a sorted set of non-negative offsets, at most [MAX_REMINDERS]. */
    fun reminders(minutes: List<Int>): List<Int> = minutes
        .filter { it in 0..MAX_REMINDER_MINUTES }
        .distinct()
        .sorted()
        .take(MAX_REMINDERS)

    /** A mail address as kept (trimmed, lower case), or null if it is not one. */
    fun alias(address: String): String? =
        address.trim().lowercase(Locale.ROOT).takeIf { EMAIL.matches(it) }

    fun aliases(addresses: List<String>): List<String> =
        addresses.mapNotNull(::alias).distinct().take(MAX_ALIASES)

    fun isPassphraseAcceptable(passphrase: CharArray): Boolean =
        passphrase.size >= MIN_PASSPHRASE_LENGTH
}
