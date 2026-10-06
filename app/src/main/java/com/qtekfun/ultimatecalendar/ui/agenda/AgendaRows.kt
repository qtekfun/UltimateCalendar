// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaEntry
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaSlot
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.ui.components.DayBadge
import com.qtekfun.ultimatecalendar.ui.components.DayBadgeState
import com.qtekfun.ultimatecalendar.ui.components.EventChip
import com.qtekfun.ultimatecalendar.ui.components.SectionHeader
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.EventDisplay
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** Above this font scale the time goes above the event, not beside it, so text never squeezes. */
private const val STACK_FONT_SCALE = 1.3f
private val TIME_COLUMN = 76.dp

/** "October 2026" above the first day with events of a month, with a line to set months apart. */
@Composable
internal fun AgendaMonthDivider(month: YearMonth, modifier: Modifier = Modifier) {
    val locale = Locale.current.platformLocale
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SectionHeader(month.format(DateTimeFormatter.ofPattern("LLLL y", locale)))
    }
}

/** The sticky header of a day: number badge (filled for today) and weekday. */
@Composable
internal fun AgendaDayHeader(date: LocalDate, today: LocalDate, modifier: Modifier = Modifier) {
    val locale = Locale.current.platformLocale
    val isToday = date == today
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    val full = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val spoken = if (isToday) stringResource(R.string.cal_day_today, full) else full
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .heightIn(min = Dimens.minTouch)
            .padding(horizontal = Spacing.l)
            .clearAndSetSemantics {
                heading()
                contentDescription = spoken
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        DayBadge(date, state = if (isToday) DayBadgeState.TODAY else DayBadgeState.NORMAL)
        Text(
            if (isToday) stringResource(R.string.cal_day_today, weekday) else weekday,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/** One event: its time, then the event as a chip (outlined while the invitation is unanswered). */
@Composable
internal fun AgendaEventRow(
    entry: AgendaEntry,
    zone: ZoneId,
    onOpen: (EventInstance) -> Unit,
    modifier: Modifier = Modifier
) {
    val event = entry.instance
    val title = event.title.ifBlank { stringResource(R.string.timegrid_untitled) }
    val times = timeLines(entry.slot, zone)
    val zoneTag = entry.otherZone?.let { ownZoneTag(entry, it) }
    val spoken = listOfNotNull(
        title,
        spokenTime(entry.slot, zone),
        event.location?.takeIf { it.isNotBlank() },
        stringResource(R.string.cal_status_pending).takeIf { entry.isPending },
        zoneTag?.let { stringResource(R.string.timegrid_other_zone, it) }
    ).joinToString(", ")
    val chip = @Composable {
        EventChip(
            title = title,
            color = entry.color ?: MaterialTheme.colorScheme.primary.toArgb(),
            display = EventDisplay.of(event.selfStatus),
            detail = event.location?.takeIf { it.isNotBlank() }
        )
    }
    val timeColumn = @Composable { TimeColumn(times, zoneTag) }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clickable(
                onClickLabel = stringResource(R.string.agenda_open_event),
                role = Role.Button
            ) { onOpen(event) }
            .padding(horizontal = Spacing.l, vertical = Spacing.xxs),
        contentAlignment = Alignment.CenterStart
    ) {
        val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE
        Box(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    timeColumn()
                    chip()
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(Modifier.width(TIME_COLUMN)) { timeColumn() }
                    Box(Modifier.weight(1f)) { chip() }
                }
            }
        }
    }
}

@Composable
private fun TimeColumn(lines: List<String>, zoneTag: String?) {
    Column {
        lines.forEachIndexed { index, line ->
            Text(
                line,
                style = MaterialTheme.typography.labelMedium,
                color = if (index == 0) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (zoneTag != null) {
            Text(
                zoneTag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun timeFormat() =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.current.platformLocale)

private fun Instant.timeIn(zone: ZoneId): String = atZone(zone).format(timeFormat())

/** The visible time lines of an entry, in the device's [zone]. */
@Composable
private fun timeLines(slot: AgendaSlot, zone: ZoneId): List<String> {
    fun at(instant: Instant) = instant.timeIn(zone)
    return when (slot) {
        AgendaSlot.AllDay -> listOf(stringResource(R.string.cal_all_day))
        is AgendaSlot.Span -> listOf(at(slot.start), at(slot.end))
        is AgendaSlot.From -> listOf(at(slot.start))
        is AgendaSlot.Until -> listOf(stringResource(R.string.agenda_until, at(slot.end)))
    }
}

@Composable
private fun spokenTime(slot: AgendaSlot, zone: ZoneId): String {
    fun at(instant: Instant) = instant.timeIn(zone)
    return when (slot) {
        AgendaSlot.AllDay -> stringResource(R.string.cal_all_day)

        is AgendaSlot.Span ->
            stringResource(R.string.timegrid_time_range, at(slot.start), at(slot.end))

        is AgendaSlot.From -> at(slot.start)

        is AgendaSlot.Until -> stringResource(R.string.agenda_until, at(slot.end))
    }
}

/** "18:00 JST": the start of the event as its own zone shows it. */
private fun ownZoneTag(entry: AgendaEntry, ownZone: ZoneId): String {
    val locale = Locale.current.platformLocale
    val start = when (val slot = entry.slot) {
        is AgendaSlot.Span -> slot.start
        is AgendaSlot.From -> slot.start
        is AgendaSlot.Until -> slot.end
        AgendaSlot.AllDay -> return ownZone.getDisplayName(TextStyle.SHORT, locale)
    }
    val clock = start.atZone(ownZone)
    return clock.format(timeFormat()) + " " + clock.format(DateTimeFormatter.ofPattern("z", locale))
}
