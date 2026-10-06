// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.accessibility

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus

/** When an event happens, already written in the user's language and zone. */
sealed interface SpokenTime {
    data object AllDay : SpokenTime

    /** "9:00 AM to 9:30 AM". */
    data class Range(val from: String, val to: String) : SpokenTime

    /** An event that only has a start to show (the agenda row of a multi-day event). */
    data class Start(val start: String) : SpokenTime

    /** The last day of a multi-day event, which ends at [end]. */
    data class Until(val end: String) : SpokenTime
}

/** Everything a screen reader says about one event; text fields are already localized. */
data class EventSpeechFacts(
    val title: String,
    val time: SpokenTime,
    val place: String? = null,
    val calendarName: String? = null,
    val status: AttendeeStatus? = null,
    /** "18:00 JST": the start as the event's own zone shows it; null in the device's zone. */
    val otherZoneTag: String? = null
)

/** The fixed phrases of the describers. The app implements them with string resources. */
interface SpeechWords {
    fun untitled(): String

    fun allDay(): String

    fun range(from: String, to: String): String

    fun until(end: String): String

    fun calendar(name: String): String

    fun status(status: AttendeeStatus): String

    fun otherZone(tag: String): String

    fun today(date: String): String

    fun dayWithoutEvents(date: String): String

    fun dayWithEvents(date: String, count: Int): String
}

/**
 * What TalkBack reads for an event block, chip or row, in one phrase: "title, time range, place,
 * calendar, status, other time zone". Colors never carry information alone: a pending
 * invitation, a "maybe" and a declined event are always named here as well as drawn differently.
 */
object EventSpeech {
    /** The statuses that are worth saying: a plain accepted event or the user's own is not. */
    private val spokenStatuses = setOf(
        AttendeeStatus.NEEDS_ACTION,
        AttendeeStatus.TENTATIVE,
        AttendeeStatus.DECLINED
    )

    fun describe(facts: EventSpeechFacts, words: SpeechWords): String = listOfNotNull(
        facts.title.trim().ifEmpty { words.untitled() },
        when (val time = facts.time) {
            SpokenTime.AllDay -> words.allDay()
            is SpokenTime.Range -> words.range(time.from, time.to)
            is SpokenTime.Start -> time.start
            is SpokenTime.Until -> words.until(time.end)
        },
        facts.place?.trim()?.takeIf { it.isNotEmpty() },
        facts.calendarName?.trim()?.takeIf { it.isNotEmpty() }?.let(words::calendar),
        statusWord(facts.status, words),
        facts.otherZoneTag?.let(words::otherZone)
    ).joinToString(SEPARATOR)

    /** The words for the user's answer, or null when there is nothing worth saying. */
    fun statusWord(status: AttendeeStatus?, words: SpeechWords): String? =
        status?.takeIf { it in spokenStatuses }?.let(words::status)

    /** "Tuesday 6 October, 3 events": a day cell of the Month view. [date] is already written. */
    fun describeDay(date: String, isToday: Boolean, events: Int, words: SpeechWords): String {
        val named = if (isToday) words.today(date) else date
        return if (events <= 0) {
            words.dayWithoutEvents(named)
        } else {
            words.dayWithEvents(named, events)
        }
    }

    private const val SEPARATOR = ", "
}
