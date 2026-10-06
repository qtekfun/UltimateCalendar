// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.month.MonthBar
import com.qtekfun.ultimatecalendar.domain.month.MonthBarStyle
import com.qtekfun.ultimatecalendar.ui.components.eventSpeech
import com.qtekfun.ultimatecalendar.ui.components.spokenTimeOf
import com.qtekfun.ultimatecalendar.ui.theme.ColorMath
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.EventDisplay
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import com.qtekfun.ultimatecalendar.ui.theme.calendarType
import com.qtekfun.ultimatecalendar.ui.theme.rememberEventChipColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val CORNER = 4.dp
private val CHIP_DOT = 6.dp
private val DOT_RING = 1.dp

/** The words an event has in the time line of a sheet or for a screen reader. */
@Composable
internal fun timeDetail(instance: EventInstance, zone: ZoneId): String =
    when (val time = instance.time) {
        is EventTime.AllDay -> stringResource(R.string.cal_all_day)

        is EventTime.Timed -> {
            val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(Locale.current.platformLocale)
            stringResource(
                R.string.month_time_range,
                time.start.atZone(zone).format(format),
                time.end.atZone(zone).format(format)
            )
        }
    }

@Composable
internal fun EventInstance.displayTitle(): String =
    title.ifBlank { stringResource(R.string.month_untitled) }

/** The event's own color, else its calendar's, else the theme's accent. */
@Composable
internal fun MonthBar.argb(): Int = color ?: MaterialTheme.colorScheme.primary.toArgb()

/** The dot of an event: its color, dark or light enough to be seen on the surface. */
@Composable
private fun rememberDotColor(argb: Int): Color {
    val surface = MaterialTheme.colorScheme.surface.toArgb()
    return remember(argb, surface) {
        Color(ColorMath.ensureContrast(argb, surface, ColorMath.GRAPHIC_CONTRAST))
    }
}

/** A small round marker of an event: solid, or a ring while the invitation is unanswered. */
@Composable
internal fun EventDot(bar: MonthBar, modifier: Modifier = Modifier) {
    val color = rememberDotColor(bar.argb())
    val shape = Modifier.size(CHIP_DOT).clip(CircleShape)
    val paint = if (bar.isPending) {
        Modifier.border(DOT_RING, color, CircleShape)
    } else {
        Modifier.background(color, CircleShape)
    }
    Spacer(modifier.then(shape).then(paint))
}

/**
 * An event in a week row: a filled bar across its days for all-day and multi-day events, a dot
 * and the title for a timed event of one day. The bar is cut flat at the sides where the event
 * goes on in the neighbouring row. Pending invitations are outlined, as everywhere.
 */
@Composable
internal fun MonthEventChip(
    bar: MonthBar,
    zone: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val display = EventDisplay.of(bar.instance.selfStatus)
    val colors = rememberEventChipColors(bar.argb(), display)
    val title = bar.instance.displayTitle()
    val spoken = eventSpeech(bar.instance, spokenTimeOf(bar.instance.time, zone))
    val open = stringResource(R.string.month_open_event)
    val strike = if (colors.strikeThrough) TextDecoration.LineThrough else null
    val click = Modifier
        .semantics(mergeDescendants = true) { contentDescription = spoken }
        .clickable(onClickLabel = open, role = Role.Button, onClick = onClick)
    when (bar.style) {
        MonthBarStyle.BAR -> {
            val shape = remember(bar.continuesBefore, bar.continuesAfter) {
                barShape(bar.continuesBefore, bar.continuesAfter)
            }
            val outline = colors.border?.let { Modifier.border(Dimens.chipBorder, it, shape) }
            Row(
                modifier
                    .then(click)
                    .clip(shape)
                    .background(colors.container)
                    .then(outline ?: Modifier)
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChipText(title, colors.content, strike)
            }
        }

        MonthBarStyle.TIMED -> Row(
            modifier.then(click).padding(horizontal = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EventDot(bar)
            Spacer(Modifier.width(Spacing.xs))
            val text = if (display == EventDisplay.DECLINED) {
                colors.content
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            ChipText(title, text, strike)
        }
    }
}

@Composable
private fun ChipText(title: String, color: Color, decoration: TextDecoration?) {
    Text(
        title,
        color = color,
        style = MaterialTheme.calendarType.eventDetail,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = decoration
    )
}

private fun barShape(continuesBefore: Boolean, continuesAfter: Boolean) = RoundedCornerShape(
    topStart = if (continuesBefore) 0.dp else CORNER,
    bottomStart = if (continuesBefore) 0.dp else CORNER,
    topEnd = if (continuesAfter) 0.dp else CORNER,
    bottomEnd = if (continuesAfter) 0.dp else CORNER
)
