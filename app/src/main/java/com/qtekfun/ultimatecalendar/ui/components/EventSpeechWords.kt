// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.accessibility.SpeechWords
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.ui.detail.ResourceWords
import com.qtekfun.ultimatecalendar.ui.detail.WordSource

/** The phrases of the describers in `domain.accessibility`, from the string resources. */
class EventSpeechWords(private val words: WordSource) : SpeechWords {
    override fun untitled() = words.text(R.string.timegrid_untitled)

    override fun allDay() = words.text(R.string.cal_all_day)

    override fun range(from: String, to: String) =
        words.text(R.string.timegrid_time_range, from, to)

    override fun until(end: String) = words.text(R.string.agenda_until, end)

    override fun calendar(name: String) = words.text(R.string.cal_speech_calendar, name)

    override fun status(status: AttendeeStatus) = when (status) {
        AttendeeStatus.NEEDS_ACTION -> words.text(R.string.cal_status_pending)
        AttendeeStatus.TENTATIVE -> words.text(R.string.cal_status_tentative)
        AttendeeStatus.DECLINED -> words.text(R.string.cal_status_declined)
        AttendeeStatus.ACCEPTED -> ""
    }

    override fun otherZone(tag: String) = words.text(R.string.timegrid_other_zone, tag)

    override fun today(date: String) = words.text(R.string.cal_day_today, date)

    override fun dayWithoutEvents(date: String) = words.text(R.string.month_day_no_events, date)

    override fun dayWithEvents(date: String, count: Int) =
        words.plural(R.plurals.month_day_events, count, date, count)
}

/** [EventSpeechWords] in the language of the current context. */
@Composable
fun rememberSpeechWords(): SpeechWords {
    val resources = LocalResources.current
    return remember(resources) { EventSpeechWords(ResourceWords(resources)) }
}

/**
 * The names of the calendars, so an event can say which one it belongs to. The shell provides
 * them; outside it (previews, tests) no calendar is named.
 */
val LocalCalendarNames = compositionLocalOf<Map<CalendarId, String>> { emptyMap() }
