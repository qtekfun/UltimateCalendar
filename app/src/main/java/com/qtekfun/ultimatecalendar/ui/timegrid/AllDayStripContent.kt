// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.timegrid.AllDayBar
import com.qtekfun.ultimatecalendar.domain.timegrid.AllDayLanes
import java.time.ZoneOffset

internal val ALL_DAY_ROW = 48.dp
private const val MAX_ALL_DAY_ROWS = 3
private const val EXPANDED_ALL_DAY_ROWS = 6

/**
 * The all-day strip above the hour grid. Without a [rowLimit] (Day, 3 days) it shows every row,
 * scrolling past three. With one (Week) it shows that many rows and the events that do not fit
 * hide behind a "+N" cell per day; a tap on it shows them all, "Less" folds them again.
 */
@Composable
internal fun AllDayStripContent(
    ui: PageDragUi,
    days: DaysLayout,
    callbacks: GridCallbacks,
    rowLimit: Int?
) {
    val page = ui.page
    if (page.allDay.isEmpty()) return
    var expanded by remember(page.days) { mutableStateOf(false) }
    val strip = AllDayLanes.limit(
        page,
        if (rowLimit != null && !expanded) rowLimit else page.allDayRows
    )
    val cap = if (rowLimit == null) MAX_ALL_DAY_ROWS else EXPANDED_ALL_DAY_ROWS
    SideEffect { ui.surface?.stripBars = strip.bars }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(max = ALL_DAY_ROW * minOf(strip.rows, cap))
            .onPlaced { ui.surface?.stripViewport = it }
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.width(days.metrics.gutter).height(ALL_DAY_ROW), Alignment.Center) {
            if (expanded) LessButton { expanded = false }
        }
        DaysArea(days.sideways, Modifier.weight(1f)) {
            Box(
                Modifier
                    .width(days.width)
                    .height(ALL_DAY_ROW * strip.rows)
                    .onPlaced { ui.surface?.strip = it }
            ) {
                val dayWidth = days.width / page.days.size
                StripBars(strip.bars, ui, callbacks, dayWidth)
                strip.hidden.forEachIndexed { day, count ->
                    if (count > 0) {
                        MoreCell(
                            count,
                            Modifier
                                .offset(dayWidth * day, ALL_DAY_ROW * (strip.rows - 1))
                                .size(dayWidth, ALL_DAY_ROW)
                        ) { expanded = true }
                    }
                }
            }
        }
    }
}

/** "+3": the all-day events of a day that do not fit; a tap shows them all. */
@Composable
private fun MoreCell(count: Int, modifier: Modifier, onClick: () -> Unit) {
    val description = pluralStringResource(R.plurals.timegrid_all_day_more, count, count)
    Box(
        modifier
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        Alignment.Center
    ) {
        Text(
            "+$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun LessButton(onClick: () -> Unit) {
    val description = stringResource(R.string.timegrid_all_day_less_description)
    Box(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        Alignment.Center
    ) {
        Text(
            stringResource(R.string.timegrid_all_day_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun StripBars(
    bars: List<AllDayBar>,
    ui: PageDragUi,
    callbacks: GridCallbacks,
    dayWidth: Dp
) {
    bars.forEach { bar ->
        val isGhost = bar.instance == ui.ghost
        val actions = moveActions(bar.instance, ui, ZoneOffset.UTC)
        AllDayEventBar(
            bar,
            callbacks.onOpenEvent,
            Modifier
                .offset(dayWidth * bar.firstDay, ALL_DAY_ROW * bar.row)
                .size(dayWidth * (bar.lastDay - bar.firstDay + 1), ALL_DAY_ROW)
                .zIndex(if (isGhost) 1f else 0f)
                .semantics { customActions = actions },
            lifted = isGhost
        )
        if (isGhost && ui.tooltip != null) {
            DragTooltip(
                ui.tooltip,
                dayWidth * bar.firstDay,
                ALL_DAY_ROW * bar.row,
                Modifier.zIndex(2f)
            )
        }
    }
}
