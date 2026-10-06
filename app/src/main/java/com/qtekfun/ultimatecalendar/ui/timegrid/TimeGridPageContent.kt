// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridLayout
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridPage
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeScale
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

private const val HOURS_PER_DAY = 24
private val LINE_WIDTH = 1.dp
private val NOW_LINE_HEIGHT = 2.dp
private val NOW_DOT = 10.dp
private val LABEL_LIFT = 8.dp
private val BLOCK_GAP = 1.dp
private val TODAY_CIRCLE = 32.dp
private val ALL_DAY_ROW = 48.dp
private const val MAX_ALL_DAY_ROWS = 3

/** What a page of the grid tells its owner. */
internal data class GridCallbacks(
    val onOpenEvent: (EventInstance) -> Unit = {},
    val onCreateAt: (LocalDateTime) -> Unit = {}
)

/** One page of Day or 3 days: day headers, the all-day strip and the scrolling hour grid. */
@Composable
internal fun TimeGridPageContent(
    page: TimeGridPage,
    failed: Boolean,
    now: GridNow,
    scroll: ScrollState,
    callbacks: GridCallbacks,
    modifier: Modifier = Modifier
) {
    val metrics = rememberGridMetrics()
    Column(modifier) {
        DayHeaders(page, now, metrics)
        AllDayStrip(page, metrics, callbacks)
        if (failed) {
            Text(
                stringResource(R.string.timegrid_failed),
                Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.error
            )
        }
        TimeGridBody(page, now, metrics, scroll, callbacks)
    }
}

@Composable
private fun DayHeaders(page: TimeGridPage, now: GridNow, metrics: GridMetrics) {
    val locale = Locale.current.platformLocale
    val today = TimeGridLayout.dateOf(now.instant, now.zone)
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(metrics.gutter))
        page.days.forEach { day ->
            val isToday = day == today
            val full = day.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
            )
            val description = if (isToday) {
                stringResource(
                    R.string.timegrid_day_today,
                    full
                )
            } else {
                full
            }
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium
                )
                DayNumber(day, isToday)
            }
        }
    }
}

@Composable
private fun DayNumber(day: LocalDate, isToday: Boolean) {
    val background = if (isToday) {
        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        Modifier
    }
    val color = if (isToday) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        day.dayOfMonth.toString(),
        Modifier
            .heightIn(min = TODAY_CIRCLE)
            .width(TODAY_CIRCLE)
            .then(background)
            .padding(top = 4.dp),
        color = color,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun AllDayStrip(page: TimeGridPage, metrics: GridMetrics, callbacks: GridCallbacks) {
    if (page.allDay.isEmpty()) return
    val rows = page.allDayRows
    val visibleHeight = ALL_DAY_ROW * minOf(rows, MAX_ALL_DAY_ROWS)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(max = visibleHeight)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.width(metrics.gutter))
        BoxWithConstraints(Modifier.weight(1f).height(ALL_DAY_ROW * rows)) {
            val dayWidth = maxWidth / page.days.size
            page.allDay.forEach { bar ->
                AllDayEventBar(
                    bar,
                    callbacks.onOpenEvent,
                    Modifier
                        .offset(dayWidth * bar.firstDay, ALL_DAY_ROW * bar.row)
                        .size(dayWidth * (bar.lastDay - bar.firstDay + 1), ALL_DAY_ROW)
                )
            }
        }
    }
}

@Composable
private fun TimeGridBody(
    page: TimeGridPage,
    now: GridNow,
    metrics: GridMetrics,
    scroll: ScrollState,
    callbacks: GridCallbacks
) {
    val height = metrics.scale.totalHeight.dp
    Row(Modifier.fillMaxWidth().verticalScroll(scroll)) {
        HourLabels(metrics, Modifier.width(metrics.gutter).height(height))
        DayColumns(page, now, metrics, callbacks, Modifier.weight(1f).height(height))
    }
}

@Composable
private fun HourLabels(metrics: GridMetrics, modifier: Modifier) {
    val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.current.platformLocale)
    Box(modifier) {
        // The label of an hour sits on its line: the first hour (midnight) needs none.
        for (hour in 1 until HOURS_PER_DAY) {
            val top = metrics.scale.offsetOf(hour * TimeScale.MINUTES_PER_HOUR).dp - LABEL_LIFT
            Text(
                LocalTime.of(hour, 0).format(format),
                Modifier
                    .offset(y = top)
                    .width(metrics.gutter)
                    .padding(end = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun DayColumns(
    page: TimeGridPage,
    now: GridNow,
    metrics: GridMetrics,
    callbacks: GridCallbacks,
    modifier: Modifier
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val scale = metrics.scale
    val dayCount = page.days.size
    BoxWithConstraints(
        modifier
            .drawBehind { drawGridLines(scale, dayCount, lineColor) }
            .pointerInput(page.days, scale) {
                detectTapGestures { tap ->
                    val day = (tap.x / size.width * dayCount).toInt().coerceIn(0, dayCount - 1)
                    val minute = scale.minuteAt(tap.y.toDp().value)
                    callbacks.onCreateAt(
                        page.days[day].atTime(
                            minute / TimeScale.MINUTES_PER_HOUR,
                            minute % TimeScale.MINUTES_PER_HOUR
                        )
                    )
                }
            }
    ) {
        val dayWidth = maxWidth / dayCount
        val minHeight = scale.offsetOf(TimeGridLayout.MIN_DURATION.toMinutes().toInt())
        page.timed.forEach { block ->
            val columnWidth = dayWidth / block.columns
            TimedEventBlock(
                block,
                now.zone,
                callbacks.onOpenEvent,
                Modifier
                    .offset(
                        dayWidth * block.dayIndex + columnWidth * block.column,
                        scale.offsetOf(block.startMinute).dp
                    )
                    .size(
                        columnWidth * block.span - BLOCK_GAP,
                        scale.heightOf(block.startMinute, block.endMinute, minHeight).dp
                    )
            )
        }
        NowLine(page, now, scale, dayWidth)
    }
}

private fun DrawScope.drawGridLines(scale: TimeScale, dayCount: Int, color: Color) {
    val stroke = LINE_WIDTH.toPx()
    for (hour in 0 until HOURS_PER_DAY) {
        val y = scale.offsetOf(hour * TimeScale.MINUTES_PER_HOUR).dp.toPx()
        drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
    }
    for (day in 0 until dayCount) {
        val x = size.width * day / dayCount
        drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
    }
}

/** The line of the current minute across today's column, if today is on the page. */
@Composable
private fun NowLine(page: TimeGridPage, now: GridNow, scale: TimeScale, dayWidth: Dp) {
    val index = page.days.indexOf(TimeGridLayout.dateOf(now.instant, now.zone))
    if (index < 0) return
    val minute = TimeGridLayout.minuteOfDay(now.instant, now.zone)
    val time = now.instant.atZone(now.zone).format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.current.platformLocale)
    )
    val description = stringResource(R.string.timegrid_now, time)
    val color = MaterialTheme.colorScheme.error
    Row(
        Modifier
            .offset(dayWidth * index, scale.offsetOf(minute).dp - NOW_DOT / 2)
            .width(dayWidth)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.size(NOW_DOT).background(color, CircleShape))
        Spacer(Modifier.weight(1f).height(NOW_LINE_HEIGHT).background(color))
    }
}
