// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import com.qtekfun.ultimatecalendar.domain.accessibility.EventSpeech
import com.qtekfun.ultimatecalendar.domain.accessibility.EventSpeechFacts
import com.qtekfun.ultimatecalendar.domain.accessibility.SpokenTime
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** [instant] as a short time of day in [zone] and the user's locale: "9:30 AM". */
fun spokenClock(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.current.platformLocale)
)

/** The time of an event as a screen reader says it, in the device's [zone]. */
fun spokenTimeOf(time: EventTime, zone: ZoneId): SpokenTime = when (time) {
    is EventTime.AllDay -> SpokenTime.AllDay

    is EventTime.Timed -> SpokenTime.Range(
        spokenClock(time.start, zone),
        spokenClock(time.end, zone)
    )
}

/**
 * The one phrase a screen reader says for an event: title, [time], place, calendar, the user's
 * answer and [otherZoneTag]. See [EventSpeech].
 */
@Composable
fun eventSpeech(instance: EventInstance, time: SpokenTime, otherZoneTag: String? = null): String {
    val words = rememberSpeechWords()
    val calendar = LocalCalendarNames.current[instance.calendarId]
    return EventSpeech.describe(
        EventSpeechFacts(
            instance.title,
            time,
            instance.location,
            calendar,
            instance.selfStatus,
            otherZoneTag
        ),
        words
    )
}
