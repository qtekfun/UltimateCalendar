// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.month

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.accessibility.EventSpeech
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.month.MonthBar
import com.qtekfun.ultimatecalendar.domain.month.MonthDensity
import com.qtekfun.ultimatecalendar.domain.month.MonthGrid
import com.qtekfun.ultimatecalendar.domain.month.MonthOverflow
import com.qtekfun.ultimatecalendar.domain.month.MonthPage
import com.qtekfun.ultimatecalendar.domain.month.MonthWeek
import com.qtekfun.ultimatecalendar.domain.navigation.WeekNumbers
import com.qtekfun.ultimatecalendar.ui.components.DayBadge
import com.qtekfun.ultimatecalendar.ui.components.DayBadgeState
import com.qtekfun.ultimatecalendar.ui.components.rememberDayBadgeSize
import com.qtekfun.ultimatecalendar.ui.components.rememberSpeechWords
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import com.qtekfun.ultimatecalendar.ui.theme.calendarType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

private val WEEK_NUMBER_WIDTH = 28.dp
private val LANE_PADDING = Spacing.xs + Spacing.xxs
private const val MAX_DOTS = 4
private const val LARGE_FONT_SCALE = 1.3f

/** What a Month page tells its owner. */
internal data class MonthCallbacks(
    val onOpenEvent: (EventInstance) -> Unit = {},
    val onOpenDay: (LocalDate) -> Unit = {},
    val onCreateAt: (LocalDate) -> Unit = {},
    val onShowDay: (DaySheet) -> Unit = {}
)

/** The events of one day, for the sheet that "+N more" opens. */
internal data class DaySheet(val date: LocalDate, val bars: List<MonthBar>)

/** The facts about a Month page that are not its events. */
internal data class MonthContext(
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    val showWeekNumbers: Boolean,
    val zone: ZoneId
)

/** One page of the Month view: weekday names, then a row per week with its days and events. */
@Composable
internal fun MonthPageContent(
    page: MonthPage,
    failed: Boolean,
    context: MonthContext,
    callbacks: MonthCallbacks,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        WeekdayHeader(page.grid.weeks.first(), context.showWeekNumbers)
        if (failed) {
            Text(
                stringResource(R.string.month_failed),
                Modifier.padding(Spacing.s),
                color = MaterialTheme.colorScheme.error
            )
        }
        page.weeks.forEach { week ->
            WeekRow(
                week,
                page.grid,
                context,
                callbacks,
                Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun WeekdayHeader(week: List<LocalDate>, withWeekNumbers: Boolean) {
    val locale = Locale.current.platformLocale
    val narrow = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val short = if (narrow) TextStyle.NARROW else TextStyle.SHORT
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        if (withWeekNumbers) Box(Modifier.width(WEEK_NUMBER_WIDTH))
        week.forEach { day ->
            val full = day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            Text(
                day.dayOfWeek.getDisplayName(short, locale),
                Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = full },
                style = MaterialTheme.calendarType.weekday,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun WeekRow(
    week: MonthWeek,
    grid: MonthGrid,
    context: MonthContext,
    callbacks: MonthCallbacks,
    modifier: Modifier
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier.drawBehind {
            drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }
    ) {
        if (context.showWeekNumbers) {
            val number = WeekNumbers.of(week.days.first(), context.firstDayOfWeek)
            val description = stringResource(R.string.shell_week_number, number)
            Text(
                number.toString(),
                Modifier
                    .width(WEEK_NUMBER_WIDTH)
                    .padding(top = Spacing.s)
                    .semantics { contentDescription = description },
                style = MaterialTheme.calendarType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            WeekCells(week, grid, context, callbacks, DpSize(maxWidth, maxHeight))
        }
    }
}

/** The days of a row with the events over them, laid out for the size the row has. */
@Composable
private fun WeekCells(
    week: MonthWeek,
    grid: MonthGrid,
    context: MonthContext,
    callbacks: MonthCallbacks,
    size: DpSize
) {
    val laneHeight = rememberLaneHeight()
    val dayHeader = rememberDayBadgeSize() + Spacing.xs
    val lanes = MonthDensity.lanes(size.height.value, dayHeader.value, laneHeight.value)
    val compact = MonthDensity.isCompact(lanes)
    val columns = remember(week) { List(week.days.size) { week.barsOn(it) } }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            week.days.forEachIndexed { col, day ->
                val state = when {
                    day == context.today -> DayBadgeState.TODAY
                    !grid.isInMonth(day) -> DayBadgeState.DIMMED
                    else -> DayBadgeState.NORMAL
                }
                DayCell(
                    day,
                    state,
                    columns[col],
                    compact,
                    callbacks,
                    Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        if (!compact) {
            val geometry = WeekGeometry(size.width / week.days.size, laneHeight, lanes, dayHeader)
            WeekEvents(week, columns, context.zone, callbacks, geometry)
        }
    }
}

/** The size of a column of the row, and how many lanes of [laneHeight] fit under a day number. */
private data class WeekGeometry(
    val cellWidth: Dp,
    val laneHeight: Dp,
    val lanes: Int,
    val dayHeader: Dp
)

/** The bars that fit and a "+N" in the last lane of each column that has more. */
@Composable
private fun WeekEvents(
    week: MonthWeek,
    columns: List<List<MonthBar>>,
    zone: ZoneId,
    callbacks: MonthCallbacks,
    geometry: WeekGeometry
) {
    val (cellWidth, laneHeight, lanes, dayHeader) = geometry
    val fit = remember(week, lanes) { MonthOverflow.fit(week, lanes) }
    Box(Modifier.fillMaxSize()) {
        fit.visible.forEach { bar ->
            val left = if (bar.continuesBefore) 0.dp else Spacing.xxs
            val right = if (bar.continuesAfter) 0.dp else Spacing.xxs
            MonthEventChip(
                bar,
                zone,
                onClick = { callbacks.onOpenEvent(bar.instance) },
                modifier = Modifier
                    .offset(
                        cellWidth * bar.firstCol + left,
                        dayHeader + laneHeight * bar.lane
                    )
                    .width(cellWidth * (bar.lastCol - bar.firstCol + 1) - left - right)
                    .height(laneHeight - Spacing.xxs)
            )
        }
        fit.more.forEachIndexed { col, count ->
            if (count > 0) {
                MoreLabel(
                    count,
                    onClick = { callbacks.onShowDay(DaySheet(week.days[col], columns[col])) },
                    modifier = Modifier
                        .offset(cellWidth * col, dayHeader + laneHeight * (lanes - 1))
                        .width(cellWidth)
                        .height(laneHeight - Spacing.xxs)
                )
            }
        }
    }
}

@Composable
private fun MoreLabel(count: Int, onClick: () -> Unit, modifier: Modifier) {
    val description = pluralStringResource(R.plurals.month_more_description, count, count)
    Box(
        modifier
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        Alignment.CenterStart
    ) {
        Text(
            stringResource(R.string.month_more, count),
            Modifier.padding(horizontal = Spacing.xs),
            style = MaterialTheme.calendarType.eventDetail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Height of a lane of events: one line of the chip text and its padding, with the font size. */
@Composable
private fun rememberLaneHeight(): Dp {
    val density = LocalDensity.current
    val style = MaterialTheme.calendarType.eventDetail
    return remember(density, style) { with(density) { style.lineHeight.toDp() } + LANE_PADDING }
}

/**
 * A day: its number, today highlighted and the neighbouring months muted; a tap opens the day, a
 * long press starts an event on it. When the cell is too small for chips it shows dots.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    day: LocalDate,
    state: DayBadgeState,
    bars: List<MonthBar>,
    compact: Boolean,
    callbacks: MonthCallbacks,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val description = dayDescription(day, state == DayBadgeState.TODAY, bars.size)
    Column(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .combinedClickable(
                onClickLabel = stringResource(R.string.month_open_day),
                onLongClickLabel = stringResource(R.string.month_create_here),
                role = Role.Button,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.onCreateAt(day)
                },
                onClick = { callbacks.onOpenDay(day) }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DayBadge(day, Modifier.padding(top = Spacing.xxs), state, announce = false)
        if (compact && bars.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                bars.take(MAX_DOTS).forEach { EventDot(it) }
            }
        }
    }
}

/** "Wednesday, 7 October 2026, 3 events" (and "today"), what a screen reader says of a day. */
@Composable
private fun dayDescription(day: LocalDate, isToday: Boolean, events: Int): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        .withLocale(Locale.current.platformLocale)
    return EventSpeech.describeDay(day.format(formatter), isToday, events, rememberSpeechWords())
}
