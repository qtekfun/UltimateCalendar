// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.intl.Locale
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Motion
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import com.qtekfun.ultimatecalendar.ui.theme.calendarType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The day-of-month number in a circle. With [onClick] the whole 48 dp around it is the touch
 * target; the circle itself stays [Dimens.dayBadge]. Talkback reads the full date.
 */
@Composable
fun DayBadge(
    date: LocalDate,
    modifier: Modifier = Modifier,
    state: DayBadgeState = DayBadgeState.NORMAL,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        when (state) {
            DayBadgeState.TODAY -> scheme.primary
            DayBadgeState.SELECTED -> scheme.primaryContainer
            else -> Color.Transparent
        },
        tween(Motion.SHORT_MS),
        label = "dayBadgeFill"
    )
    val content = when (state) {
        DayBadgeState.TODAY -> scheme.onPrimary
        DayBadgeState.SELECTED -> scheme.onPrimaryContainer
        DayBadgeState.DIMMED -> scheme.onSurfaceVariant.copy(alpha = DIMMED_ALPHA)
        DayBadgeState.NORMAL -> scheme.onSurface
    }
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        .withLocale(Locale.current.platformLocale)
    val full = date.format(formatter)
    val description = if (state == DayBadgeState.TODAY) {
        stringResource(R.string.cal_day_today, full)
    } else {
        full
    }
    val clickable = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier
            .then(clickable)
            .clearAndSetSemantics {
                contentDescription = description
                selected = state == DayBadgeState.SELECTED || state == DayBadgeState.TODAY
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(Dimens.dayBadge)
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.calendarType.dayNumber,
                color = content
            )
        }
    }
}

private const val DIMMED_ALPHA = 0.8f

@ComponentPreviews
@Composable
internal fun DayBadgePreview() {
    PreviewSurface {
        Row(Modifier.padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            val date = PreviewToday
            DayBadgeState.entries.forEach { DayBadge(date, state = it, onClick = {}) }
        }
    }
}
