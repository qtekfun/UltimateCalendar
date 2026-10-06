// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.timegrid.AllDayLanes
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridLayout
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridPage
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeScale
import com.qtekfun.ultimatecalendar.domain.timegrid.TimedBlock
import com.qtekfun.ultimatecalendar.ui.components.DayBadge
import com.qtekfun.ultimatecalendar.ui.components.DayBadgeState
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

/** What a page of the grid tells its owner. */
internal data class GridCallbacks(
    val onOpenEvent: (EventInstance) -> Unit = {},
    val onCreateAt: (LocalDateTime) -> Unit = {},
    /** Picking events up to move them or change their duration (T18). */
    val editing: GridEditing = GridEditing()
)

/** What differs between the views that share this page: the Week view sets both. */
internal data class GridOptions(
    /** The number to show in the corner of the day headers, or null for none. */
    val weekNumber: Int? = null,
    /** Rows of the all-day strip before the rest hide behind "+N", or null to scroll them. */
    val allDayRowLimit: Int? = null
)

/**
 * One page of Day, 3 days or Week: day headers, the all-day strip and the scrolling hour grid.
 * The day columns share the page width; when they would be narrower than
 * [GridMetrics.minDayWidth] they scroll sideways together, headers and strip included, while the
 * hour labels stay put.
 */
@Composable
internal fun TimeGridPageContent(
    page: TimeGridPage,
    failed: Boolean,
    now: GridNow,
    scroll: ScrollState,
    callbacks: GridCallbacks,
    modifier: Modifier = Modifier,
    options: GridOptions = GridOptions(),
    drag: PageDrag? = null
) {
    val metrics = rememberGridMetrics()
    val ui = rememberPageDragUi(page, now.zone, drag)
    BoxWithConstraints(modifier) {
        val available = maxWidth - metrics.gutter
        val daysWidth = maxOf(available, metrics.minDayWidth * page.days.size)
        val sideways = rememberScrollState()
        SideEffect {
            ui.surface?.let {
                it.page = ui.page
                it.scale = metrics.scale
                it.zone = now.zone
                it.sideways = sideways
            }
        }
        Column {
            val days = DaysLayout(metrics, daysWidth, sideways)
            DayHeaders(page, now, days, options.weekNumber)
            AllDayStripContent(ui, days, callbacks, options.allDayRowLimit)
            if (failed) {
                Text(
                    stringResource(R.string.timegrid_failed),
                    Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
            TimeGridBody(ui, now, days, scroll, callbacks)
        }
    }
}

/**
 * Where the day columns sit: the [metrics] of the grid, the [width] all the columns take together
 * (more than the screen when they scroll) and the [sideways] scroll the headers, the all-day
 * strip and the grid share.
 */
internal class DaysLayout(val metrics: GridMetrics, val width: Dp, val sideways: ScrollState)

/** The area of the day columns: its content is as wide as it needs, scrolling sideways. */
@Composable
internal fun DaysArea(sideways: ScrollState, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.horizontalScroll(sideways)) { content() }
}

@Composable
private fun DayHeaders(page: TimeGridPage, now: GridNow, days: DaysLayout, weekNumber: Int?) {
    val locale = Locale.current.platformLocale
    val today = TimeGridLayout.dateOf(now.instant, now.zone)
    Row(Modifier.fillMaxWidth()) {
        WeekNumberCell(weekNumber, Modifier.width(days.metrics.gutter))
        DaysArea(days.sideways, Modifier.weight(1f)) {
            Row(Modifier.width(days.width)) {
                page.days.forEach { day ->
                    val isToday = day == today
                    val full = day.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
                    )
                    val description = if (isToday) {
                        stringResource(R.string.timegrid_day_today, full)
                    } else {
                        full
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .clearAndSetSemantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                        DayBadge(
                            day,
                            state = if (isToday) DayBadgeState.TODAY else DayBadgeState.NORMAL
                        )
                    }
                }
            }
        }
    }
}

/** The week number in the corner above the hour labels, when the setting asks for it. */
@Composable
private fun WeekNumberCell(weekNumber: Int?, modifier: Modifier) {
    if (weekNumber == null) {
        Spacer(modifier)
        return
    }
    val description = stringResource(R.string.timegrid_week_number, weekNumber)
    Box(modifier.clearAndSetSemantics { contentDescription = description }, Alignment.Center) {
        Text(
            stringResource(R.string.timegrid_week_number_short, weekNumber),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun TimeGridBody(
    ui: PageDragUi,
    now: GridNow,
    days: DaysLayout,
    scroll: ScrollState,
    callbacks: GridCallbacks
) {
    val metrics = days.metrics
    val height = metrics.scale.totalHeight.dp
    Row(
        Modifier
            .fillMaxWidth()
            .onPlaced { ui.surface?.viewport = it }
            .verticalScroll(scroll)
    ) {
        HourLabels(metrics, Modifier.width(metrics.gutter).height(height))
        DaysArea(days.sideways, Modifier.weight(1f).height(height)) {
            DayColumns(ui, now, metrics, callbacks, Modifier.width(days.width).height(height))
        }
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
    ui: PageDragUi,
    now: GridNow,
    metrics: GridMetrics,
    callbacks: GridCallbacks,
    modifier: Modifier
) {
    val page = ui.page
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val scale = metrics.scale
    val dayCount = page.days.size
    BoxWithConstraints(
        modifier
            .onPlaced { ui.surface?.columns = it }
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
        val lifted = page.timed.filter { it.instance == ui.ghost }
        page.timed.forEach { block ->
            val columnWidth = dayWidth / block.columns
            val isGhost = block in lifted
            val actions = moveActions(block.instance, ui, now.zone)
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
                    .zIndex(if (isGhost) 1f else 0f)
                    .semantics { customActions = actions },
                lifted = isGhost
            )
        }
        NowLine(page, now, scale, dayWidth)
        TooltipOver(lifted.firstOrNull(), ui.tooltip, scale, dayWidth)
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
