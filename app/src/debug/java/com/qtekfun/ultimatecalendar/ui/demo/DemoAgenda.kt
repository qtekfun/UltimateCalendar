// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.ui.components.AllDayChip
import com.qtekfun.ultimatecalendar.ui.components.DayBadge
import com.qtekfun.ultimatecalendar.ui.components.DayBadgeState
import com.qtekfun.ultimatecalendar.ui.components.EmptyState
import com.qtekfun.ultimatecalendar.ui.components.EventChip
import com.qtekfun.ultimatecalendar.ui.theme.EventDisplay
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

private val DayColumn = 56.dp

/** One day of the demo agenda: its date and the events that touch it. */
internal data class DemoDay(val date: LocalDate, val events: List<EventInstance>)

/** The days of [days] with their events, from the demo [instances] (a stand-in for T14). */
internal fun demoDays(
    days: List<LocalDate>,
    instances: List<EventInstance>,
    zone: ZoneId
): List<DemoDay> = days.map { day ->
    DemoDay(
        day,
        instances.filter { instance ->
            when (val time = instance.time) {
                is EventTime.AllDay -> day >= time.startDate && day < time.endDate
                is EventTime.Timed -> time.start.atZone(zone).toLocalDate() == day
            }
        }
    )
}

/** A throw-away agenda to show the components with data; T14 builds the real Agenda. */
@Composable
internal fun DemoAgenda(
    days: List<DemoDay>,
    today: LocalDate,
    colors: Map<CalendarId, Int>,
    zone: ZoneId,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpen: (EventInstance) -> Unit = {}
) {
    if (days.all { it.events.isEmpty() }) {
        EmptyState(
            title = stringResource(R.string.cal_empty_title),
            body = stringResource(R.string.cal_empty_body),
            modifier = modifier.fillMaxSize().padding(padding)
        )
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + FAB_CLEARANCE
        )
    ) {
        items(days.filter { it.events.isNotEmpty() }, key = { it.date.toEpochDay() }) { day ->
            DayRow(day, today, colors, zone, onOpen)
        }
    }
}

private val FAB_CLEARANCE = 88.dp

@Composable
private fun DayRow(
    day: DemoDay,
    today: LocalDate,
    colors: Map<CalendarId, Int>,
    zone: ZoneId,
    onOpen: (EventInstance) -> Unit
) {
    val locale = Locale.current.platformLocale
    val weekday = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        Column(Modifier.width(DayColumn), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                weekday,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DayBadge(
                day.date,
                state = if (day.date == today) DayBadgeState.TODAY else DayBadgeState.NORMAL
            )
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            day.events.forEach { event ->
                val color = event.color ?: colors[event.calendarId] ?: 0
                val display = EventDisplay.of(event.selfStatus)
                when (val time = event.time) {
                    is EventTime.AllDay -> AllDayChip(event.title, color, display = display)

                    is EventTime.Timed -> EventChip(
                        event.title,
                        color,
                        detail = timeRange(time, zone, event.location),
                        display = display,
                        onClick = { onOpen(event) }
                    )
                }
            }
        }
    }
}

private fun timeRange(time: EventTime.Timed, zone: ZoneId, place: String?): String {
    val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val start = time.start.atZone(zone).format(format)
    val end = time.end.atZone(zone).format(format)
    val other = if (time.zone != zone) " (${time.zone.id.substringAfter('/')})" else ""
    return listOfNotNull("$start - $end$other", place).joinToString(" · ")
}
