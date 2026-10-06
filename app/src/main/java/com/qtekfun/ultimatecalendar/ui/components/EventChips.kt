// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.ui.theme.CalendarShapes
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.EventChipColors
import com.qtekfun.ultimatecalendar.ui.theme.EventDisplay
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import com.qtekfun.ultimatecalendar.ui.theme.calendarType
import com.qtekfun.ultimatecalendar.ui.theme.rememberEventChipColors

/** The words a screen reader adds for events that are not plainly confirmed. */
@Composable
internal fun EventDisplay.statusLabel(): String? = when (this) {
    EventDisplay.CONFIRMED -> null
    EventDisplay.TENTATIVE -> stringResource(R.string.cal_status_tentative)
    EventDisplay.PENDING -> stringResource(R.string.cal_status_pending)
    EventDisplay.DECLINED -> stringResource(R.string.cal_status_declined)
}

/**
 * An event in a time grid, a month cell or the agenda: title, optional [detail] line (time and
 * place) and the colors of its [display] state. Height wraps the text, so large fonts never clip.
 * Pass [maxLines] to cap the title where space is tight (month cells use 1).
 */
@Composable
fun EventChip(
    title: String,
    color: Int,
    modifier: Modifier = Modifier,
    detail: String? = null,
    display: EventDisplay = EventDisplay.of(null),
    maxLines: Int = 2,
    onClick: (() -> Unit)? = null
) {
    val colors = rememberEventChipColors(color, display)
    val status = display.statusLabel()
    val spoken = listOfNotNull(title, detail, status).joinToString(", ")
    ChipBox(
        colors,
        modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        PaddingValues(horizontal = Spacing.s - Spacing.xxs, vertical = Spacing.xs),
        onClick
    ) {
        Column {
            ChipTitle(title, colors, maxLines)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.calendarType.eventDetail,
                    color = colors.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (colors.strikeThrough) TextDecoration.LineThrough else null
                )
            }
        }
    }
}

/** An all-day event: a one-line bar across the days it covers. */
@Composable
fun AllDayChip(
    title: String,
    color: Int,
    modifier: Modifier = Modifier,
    display: EventDisplay = EventDisplay.of(null),
    onClick: (() -> Unit)? = null
) {
    val colors = rememberEventChipColors(color, display)
    val allDay = stringResource(R.string.cal_all_day)
    val spoken = listOfNotNull(title, allDay, display.statusLabel()).joinToString(", ")
    ChipBox(
        colors,
        modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        PaddingValues(horizontal = Spacing.s - Spacing.xxs, vertical = Spacing.xxs),
        onClick
    ) {
        ChipTitle(title, colors, maxLines = 1)
    }
}

@Composable
private fun ChipTitle(title: String, colors: EventChipColors, maxLines: Int) {
    Text(
        title,
        style = MaterialTheme.calendarType.eventTitle,
        color = colors.content,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (colors.strikeThrough) TextDecoration.LineThrough else null
    )
}

@Composable
private fun ChipBox(
    colors: EventChipColors,
    modifier: Modifier,
    padding: PaddingValues,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val border = colors.border?.let {
        Modifier.border(Dimens.chipBorder, it, CalendarShapes.eventChip)
    }
    val click = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier
            .heightIn(min = Dimens.chipMinHeight)
            .clip(CalendarShapes.eventChip)
            .background(colors.container)
            .then(border ?: Modifier)
            .then(click)
            .padding(padding)
    ) { content() }
}

@ComponentPreviews
@Composable
internal fun EventChipPreview() {
    PreviewSurface {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val blue = 0xFF1A73E8.toInt()
            val yellow = 0xFFF6BF26.toInt()
            EventChip("Team standup", blue, detail = "9:00 - 9:30 AM")
            val red = 0xFFD50000.toInt()
            val green = 0xFF0B8043.toInt()
            val pending = EventDisplay.of(AttendeeStatus.NEEDS_ACTION)
            EventChip("Dentist (invitation)", red, detail = "Not answered", display = pending)
            val maybe = EventDisplay.of(AttendeeStatus.TENTATIVE)
            EventChip("Maybe: lunch", green, detail = "12:30 PM", display = maybe)
            val declined = EventDisplay.of(AttendeeStatus.DECLINED)
            EventChip("Declined review", blue, detail = "3:00 PM", display = declined)
            EventChip("Yellow needs dark text", yellow, detail = "Contrast check")
            AllDayChip("Holiday", 0xFF8E24AA.toInt())
        }
    }
}
