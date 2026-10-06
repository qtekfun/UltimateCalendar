// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.ui.components.CalendarBottomSheet
import com.qtekfun.ultimatecalendar.ui.components.EventChip
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.EventDisplay
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SHEET_LIST_MAX_HEIGHT = 360.dp

/**
 * Every event of a day that did not fit its cell, in a bottom sheet (Google Calendar's "+N more"):
 * a tap opens the event, and "Open day" goes to the Day view of that date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthDaySheet(
    sheet: DaySheet,
    zone: ZoneId,
    onOpenEvent: (EventInstance) -> Unit,
    onOpenDay: () -> Unit,
    onDismiss: () -> Unit
) {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        .withLocale(Locale.current.platformLocale)
    CalendarBottomSheet(onDismiss) {
        Text(
            sheet.date.format(format),
            Modifier.padding(horizontal = Spacing.l).semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )
        Column(
            Modifier
                .heightIn(max = SHEET_LIST_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            sheet.bars.forEach { bar ->
                EventChip(
                    title = bar.instance.displayTitle(),
                    color = bar.argb(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch),
                    detail = timeDetail(bar.instance, zone),
                    display = EventDisplay.of(bar.instance.selfStatus),
                    onClick = { onOpenEvent(bar.instance) }
                )
            }
        }
        TextButton(
            onClick = onOpenDay,
            modifier = Modifier.padding(horizontal = Spacing.s).heightIn(min = Dimens.minTouch)
        ) { Text(stringResource(R.string.month_day_sheet_open)) }
    }
}
