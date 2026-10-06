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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.timegrid.AllDayBar
import com.qtekfun.ultimatecalendar.domain.timegrid.TimedBlock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val LIGHT_TEXT_LUMINANCE = 0.5f
private val OUTLINE_WIDTH = 2.dp
private val CORNER = 4.dp
private val BAR_MARGIN = 8.dp

/** How an event is painted: filled with its color, or only outlined while the invitation is open. */
internal data class EventPaint(val fill: Color, val border: BorderStroke?, val text: Color)

@Composable
internal fun eventPaint(argb: Int?, pending: Boolean): EventPaint {
    val color = argb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    return if (pending) {
        EventPaint(
            Color.Transparent,
            BorderStroke(OUTLINE_WIDTH, color),
            MaterialTheme.colorScheme.onSurface
        )
    } else {
        EventPaint(
            color,
            null,
            if (color.luminance() >
                LIGHT_TEXT_LUMINANCE
            ) {
                Color.Black
            } else {
                Color.White
            }
        )
    }
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

/** What TalkBack reads for a timed event: title, time, place, open invitation, other zone. */
@Composable
private fun describe(block: TimedBlock, zone: ZoneId): String {
    val event = block.instance
    val time = event.time as EventTime.Timed
    val (from, to) = timeRange(time, zone)
    val parts = listOfNotNull(
        event.title.ifBlank { stringResource(R.string.timegrid_untitled) },
        stringResource(R.string.timegrid_time_range, from, to),
        event.location?.takeIf { it.isNotBlank() },
        stringResource(R.string.timegrid_pending).takeIf { block.isPending },
        block.otherZone?.let { stringResource(R.string.timegrid_other_zone, ownZoneTag(time, it)) }
    )
    return parts.joinToString(", ")
}

@Composable
internal fun TimedEventBlock(
    block: TimedBlock,
    zone: ZoneId,
    onClick: (EventInstance) -> Unit,
    modifier: Modifier = Modifier
) {
    val paint = eventPaint(block.color, block.isPending)
    val description = describe(block, zone)
    val time = block.instance.time as EventTime.Timed
    val detailLabel = stringResource(R.string.timegrid_event_detail)
    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(onClickLabel = detailLabel) { onClick(block.instance) },
        shape = RoundedCornerShape(CORNER),
        color = paint.fill,
        contentColor = paint.text,
        border = paint.border
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(
                block.instance.title.ifBlank { stringResource(R.string.timegrid_untitled) },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val (from, to) = timeRange(time, zone)
            Text(
                "$from - $to",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    modifier: Modifier = Modifier
) {
    val paint = eventPaint(bar.color, bar.isPending)
    val title = bar.instance.title.ifBlank { stringResource(R.string.timegrid_untitled) }
    val description = listOfNotNull(
        title,
        stringResource(R.string.timegrid_all_day),
        stringResource(R.string.timegrid_pending).takeIf { bar.isPending }
    ).joinToString(", ")
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
        border = paint.border
    ) {
        Text(
            title,
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
