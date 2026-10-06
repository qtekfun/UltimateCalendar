// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.accessibility.SpokenTime
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.timegrid.AllDayBar
import com.qtekfun.ultimatecalendar.domain.timegrid.TimedBlock
import com.qtekfun.ultimatecalendar.ui.components.eventSpeech
import com.qtekfun.ultimatecalendar.ui.components.spokenTimeOf
import com.qtekfun.ultimatecalendar.ui.theme.EventDisplay
import com.qtekfun.ultimatecalendar.ui.theme.rememberEventChipColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val OUTLINE_WIDTH = 2.dp
private val CORNER = 4.dp
private val BAR_MARGIN = 8.dp
private val LIFT = 8.dp
private val HANDLE_WIDTH = 24.dp
private val HANDLE_HEIGHT = 4.dp
private val HANDLE_INSET = 3.dp
private const val HANDLE_ALPHA = 0.7f

/** The little bar at the bottom of an event held in the hand: the end can be dragged (T18). */
private fun Modifier.dragHandle(color: Color): Modifier = drawBehind {
    val width = HANDLE_WIDTH.toPx()
    val height = HANDLE_HEIGHT.toPx()
    drawRoundRect(
        color.copy(alpha = HANDLE_ALPHA),
        Offset((size.width - width) / 2, size.height - height - HANDLE_INSET.toPx()),
        Size(width, height),
        CornerRadius(height / 2)
    )
}

/** How an event is painted: [EventDisplay] decides fill, outline and strike-through. */
internal data class EventPaint(
    val fill: Color,
    val border: BorderStroke?,
    val text: Color,
    val strike: Boolean
)

@Composable
internal fun eventPaint(argb: Int?, selfStatus: AttendeeStatus?): EventPaint {
    val colors = rememberEventChipColors(
        argb ?: MaterialTheme.colorScheme.primary.toArgb(),
        EventDisplay.of(selfStatus)
    )
    return EventPaint(
        colors.container,
        colors.border?.let { BorderStroke(OUTLINE_WIDTH, it) },
        colors.content,
        colors.strikeThrough
    )
}

private fun timeFormat() =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.current.platformLocale)

/** The time of day of a timed event in the device's zone. */
private fun timeRange(time: EventTime.Timed, zone: ZoneId): Pair<String, String> {
    val format = timeFormat()
    return time.start.atZone(zone).format(format) to time.end.atZone(zone).format(format)
}

/** "18:00 JST": the start of the event as its own zone shows it. */
private fun ownZoneTag(time: EventTime.Timed, ownZone: ZoneId): String {
    val locale = Locale.current.platformLocale
    val clock = time.start.atZone(ownZone)
    return clock.format(timeFormat()) + " " + clock.format(DateTimeFormatter.ofPattern("z", locale))
}

@Composable
internal fun TimedEventBlock(
    block: TimedBlock,
    zone: ZoneId,
    onClick: (EventInstance) -> Unit,
    modifier: Modifier = Modifier,
    lifted: Boolean = false
) {
    val paint = eventPaint(block.color, block.instance.selfStatus)
    val time = block.instance.time as EventTime.Timed
    val description = eventSpeech(
        block.instance,
        spokenTimeOf(time, zone),
        block.otherZone?.let { ownZoneTag(time, it) }
    )
    val detailLabel = stringResource(R.string.timegrid_event_detail)
    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(onClickLabel = detailLabel) { onClick(block.instance) },
        shape = RoundedCornerShape(CORNER),
        color = paint.fill,
        contentColor = paint.text,
        border = paint.border,
        shadowElevation = if (lifted) LIFT else 0.dp
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (lifted) Modifier.dragHandle(paint.text) else Modifier)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                block.instance.title.ifBlank { stringResource(R.string.timegrid_untitled) },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = strike(paint)
            )
            val (from, to) = timeRange(time, zone)
            Text(
                "$from - $to",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = strike(paint)
            )
            block.otherZone?.let {
                Text(
                    ownZoneTag(time, it),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AllDayEventBar(
    bar: AllDayBar,
    onClick: (EventInstance) -> Unit,
    modifier: Modifier = Modifier,
    lifted: Boolean = false
) {
    val paint = eventPaint(bar.color, bar.instance.selfStatus)
    val title = bar.instance.title.ifBlank { stringResource(R.string.timegrid_untitled) }
    val description = eventSpeech(bar.instance, SpokenTime.AllDay)
    val detailLabel = stringResource(R.string.timegrid_event_detail)
    // The row is 48 dp tall and all of it is the touch target; the painted bar is 32 dp.
    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(onClickLabel = detailLabel) { onClick(bar.instance) }
            .padding(vertical = BAR_MARGIN),
        shape = RoundedCornerShape(CORNER),
        color = paint.fill,
        contentColor = paint.text,
        border = paint.border,
        shadowElevation = if (lifted) LIFT else 0.dp
    ) {
        Text(
            title,
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = strike(paint)
        )
    }
}

private fun strike(paint: EventPaint) = if (paint.strike) TextDecoration.LineThrough else null
