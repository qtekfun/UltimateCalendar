// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.timegrid.DayBounds
import com.qtekfun.ultimatecalendar.domain.timegrid.DragPreview
import com.qtekfun.ultimatecalendar.domain.timegrid.EventDrag
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridPage
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeScale
import com.qtekfun.ultimatecalendar.domain.timegrid.TimedBlock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

private const val NUDGE_MINUTES = 15f
private val TOOLTIP_GAP = 4.dp

/** The drag as one page of the pager sees it: its place in the pager and a change being saved. */
internal class PageDrag(val controller: DragController, val index: Int, val pending: PendingMove?)

/**
 * What a page draws while an event is held or being saved (T18): [page] has the event at its new
 * time and the others laid out around it, [ghost] is that event, lifted, with [tooltip] telling
 * its time. Without a drag it is the page as it is.
 */
@Immutable
internal class PageDragUi(
    val page: TimeGridPage,
    val ghost: EventInstance?,
    val tooltip: String?,
    val surface: PageSurface?,
    val editing: GridEditing
) {
    val bounds: DayBounds get() = DayBounds.of(page)
}

private class Shown(val instance: EventInstance, val time: EventTime, val color: Int?)

@Composable
internal fun rememberPageDragUi(page: TimeGridPage, zone: ZoneId, drag: PageDrag?): PageDragUi {
    if (drag == null) return remember(page) { PageDragUi(page, null, null, null, GridEditing()) }
    val controller = drag.controller
    val surface = remember(controller, drag.index) { controller.surface(drag.index) }
    DisposableEffect(controller, drag.index) { onDispose { controller.forget(drag.index) } }
    val held = controller.session
    val shown = held?.let { Shown(it.instance, it.target, it.color) }
        ?: drag.pending?.let { Shown(it.instance, it.newTime, null) }
    val drawn = remember(page, zone, shown?.instance, shown?.time, shown?.color) {
        shown?.let { DragPreview.apply(page, zone, it.instance, it.time, it.color) } ?: page
    }
    val tooltip = held?.let { dragTooltip(it.target, zone) }
    return PageDragUi(
        drawn,
        shown?.let {
            it.instance.copy(time = it.time)
        },
        tooltip,
        surface,
        controller.editing
    )
}

/** "Thu, 9:15 AM to 10:15 AM" for a timed event; "Mar 11 to Mar 13" for an all-day one. */
@Composable
private fun dragTooltip(target: EventTime, zone: ZoneId): String {
    val locale = Locale.current.platformLocale
    return when (target) {
        is EventTime.Timed -> {
            val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            val from = target.start.atZone(zone)
            val range = stringResource(
                R.string.timegrid_time_range,
                from.format(format),
                target.end.atZone(zone).format(format)
            )
            stringResource(
                R.string.drag_tooltip,
                from.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                range
            )
        }

        is EventTime.AllDay -> {
            val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            stringResource(
                R.string.timegrid_time_range,
                target.startDate.format(format),
                target.lastDate.format(format)
            )
        }
    }
}

/**
 * The small label that follows an event being dragged and says when it would be. [x] and [y] are
 * the top left of the event; the label sits above it, or below when there is no room above, and
 * is kept inside the width of the page.
 */
@Composable
internal fun DragTooltip(text: String, x: Dp, y: Dp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints(maxWidth = constraints.maxWidth))
                val left = x.roundToPx().coerceIn(
                    0,
                    maxOf(0, constraints.maxWidth - placeable.width)
                )
                val above = y.roundToPx() - placeable.height - TOOLTIP_GAP.roundToPx()
                val top = if (above >= 0) above else y.roundToPx() + TOOLTIP_GAP.roundToPx()
                layout(constraints.maxWidth, top + placeable.height) { placeable.place(left, top) }
            },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

/**
 * What TalkBack offers instead of dragging (T18): move a timed event 15 minutes earlier or
 * later, make it 15 minutes longer or shorter, or move an all-day event a day earlier or later.
 * Nothing for an event that cannot be edited. They save through the same path as a drag.
 */
@Composable
internal fun moveActions(
    instance: EventInstance,
    ui: PageDragUi,
    zone: ZoneId
): List<CustomAccessibilityAction> {
    if (!ui.editing.canPickUp(instance)) return emptyList()
    val bounds = ui.bounds
    val labels = MoveLabels(
        stringResource(R.string.drag_action_earlier),
        stringResource(R.string.drag_action_later),
        stringResource(R.string.drag_action_longer),
        stringResource(R.string.drag_action_shorter),
        stringResource(R.string.drag_action_day_earlier),
        stringResource(R.string.drag_action_day_later)
    )
    fun action(label: String, change: (EventTime) -> EventTime) = CustomAccessibilityAction(label) {
        val moved = change(instance.time)
        val changed = moved != instance.time
        if (changed) ui.editing.onMove(instance, moved)
        changed
    }
    return when (instance.time) {
        is EventTime.Timed -> listOf(
            action(labels.earlier) { move(it, zone, -NUDGE_MINUTES, bounds) },
            action(labels.later) { move(it, zone, NUDGE_MINUTES, bounds) },
            action(labels.longer) { resize(it, zone, NUDGE_MINUTES) },
            action(labels.shorter) { resize(it, zone, -NUDGE_MINUTES) }
        )

        is EventTime.AllDay -> listOf(
            action(labels.dayEarlier) { moveDays(it, -1, bounds) },
            action(labels.dayLater) { moveDays(it, 1, bounds) }
        )
    }
}

private class MoveLabels(
    val earlier: String,
    val later: String,
    val longer: String,
    val shorter: String,
    val dayEarlier: String,
    val dayLater: String
)

private fun move(time: EventTime, zone: ZoneId, minutes: Float, bounds: DayBounds) =
    (time as? EventTime.Timed)?.let { EventDrag.move(it, zone, 0, minutes, bounds, step = 1) }
        ?: time

private fun resize(time: EventTime, zone: ZoneId, minutes: Float) =
    (time as? EventTime.Timed)?.let { EventDrag.resize(it, zone, minutes, step = 1) } ?: time

private fun moveDays(time: EventTime, days: Int, bounds: DayBounds) =
    (time as? EventTime.AllDay)?.let { EventDrag.moveAllDay(it, days, bounds) } ?: time

/** The tooltip with the time of the event held, above its top (the first piece, if it spans days). */
@Composable
internal fun TooltipOver(block: TimedBlock?, text: String?, scale: TimeScale, dayWidth: Dp) {
    if (block == null || text == null) return
    val x = dayWidth * block.dayIndex + dayWidth / block.columns * block.column
    DragTooltip(text, x, scale.offsetOf(block.startMinute).dp, Modifier.zIndex(2f))
}

/** Reports where this part of the page is, for the long press to find what is under a finger. */
internal fun Modifier.onPlaced(report: (LayoutCoordinates) -> Unit): Modifier =
    onGloballyPositioned(report)
