// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.domain.navigation.PeriodPages
import com.qtekfun.ultimatecalendar.domain.navigation.ViewPeriods
import com.qtekfun.ultimatecalendar.domain.navigation.WeekNumbers
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeScale
import com.qtekfun.ultimatecalendar.ui.adaptive.WithAdaptiveGridScale
import com.qtekfun.ultimatecalendar.ui.shell.ShellActions
import com.qtekfun.ultimatecalendar.ui.shell.ShellUiState
import com.qtekfun.ultimatecalendar.ui.theme.rememberReduceMotion
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

private const val FIRST_HOUR_SHOWN = 7

/** Rows of all-day events the Week header shows before the rest hide behind "+N". */
private const val WEEK_ALL_DAY_ROWS = 3

/** Where the pages of a grid get their events: the ViewModel, or fixed data in previews. */
internal interface TimeGridPages {
    fun initial(range: DateRange): TimeGridState

    fun page(range: DateRange): Flow<TimeGridState>
}

/** Day, 3 days and Week inside the shell, fed by [TimeGridViewModel] (RF-03). */
@Composable
fun TimeGridScreen(
    state: ShellUiState,
    actions: ShellActions,
    modifier: Modifier = Modifier,
    viewModel: TimeGridViewModel = viewModel(),
    moves: EventMoveViewModel = viewModel()
) {
    val now by viewModel.now.collectAsStateWithLifecycle()
    val pages = remember(viewModel) {
        object : TimeGridPages {
            override fun initial(range: DateRange) = viewModel.emptyPage(range)

            override fun page(range: DateRange) = viewModel.page(range)
        }
    }
    WithAdaptiveGridScale {
        MoveFlow(moves) { editing, pending ->
            TimeGridView(
                state = state,
                now = now,
                pages = pages,
                callbacks = GridCallbacks(actions.onOpenEvent, actions.onCreateAt, editing),
                onSelectDate = actions.onSelectDate,
                modifier = modifier,
                pending = pending
            )
        }
    }
}

/**
 * A swipeable run of day-grid pages (a day, three days or a week). [date] is the shell's selected
 * date: the pager follows it (Today, the date picker) and reports the date it settles on after a
 * swipe through [onSelectDate]. The vertical scroll is shared, so every page shows the same hours.
 */
@Composable
internal fun TimeGridView(
    state: ShellUiState,
    now: GridNow,
    pages: TimeGridPages,
    callbacks: GridCallbacks,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    pending: PendingMove? = null
) {
    val view = state.view
    val date = state.date
    var anchor by remember(view) { mutableStateOf(date) }
    val periods = remember(view, anchor) { PeriodPages(view, anchor) }
    val selected by rememberUpdatedState(date)
    val scroll = rememberFirstHourScroll()
    val drag = rememberDragController(callbacks.editing, busy = pending != null)
    val reduceMotion = rememberReduceMotion()
    Box(modifier.dragGestures(drag)) {
        key(periods) {
            val pager = rememberPagerState(periods.pageOf(date) ?: PeriodPages.CENTER) {
                PeriodPages.COUNT
            }
            SideEffect { drag.currentPage = { pager.currentPage } }
            DragEdgeEffect(drag, pager, scroll, reduceMotion)
            BackHandler(enabled = drag.active) { drag.cancel() }
            LaunchedEffect(date) {
                val target = periods.pageOf(date)
                when {
                    target == null -> anchor = date
                    target != pager.currentPage -> pager.animateScrollToPage(target)
                }
            }
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { page ->
                    val settled = periods.dateAt(page)
                    if (settled != selected) onSelectDate(settled)
                }
            }
            HorizontalPager(
                pager,
                Modifier.fillMaxSize(),
                userScrollEnabled = !drag.active
            ) { page ->
                val range = ViewPeriods.range(view, periods.dateAt(page), state.firstDayOfWeek)
                val content by remember(range) { pages.page(range) }
                    .collectAsStateWithLifecycle(remember(range) { pages.initial(range) })
                TimeGridPageContent(
                    content.page,
                    content.failed,
                    now,
                    scroll,
                    callbacks,
                    options = gridOptions(state, range),
                    drag = PageDrag(drag, page, pending)
                )
            }
        }
    }
}

/** What differs in the Week view: week numbers and a limit of all-day rows. */
private fun gridOptions(state: ShellUiState, range: DateRange): GridOptions =
    if (state.view == CalendarView.WEEK) {
        GridOptions(
            weekNumber = range.start
                .takeIf { state.showWeekNumbers }
                ?.let { WeekNumbers.of(it, state.firstDayOfWeek) },
            allDayRowLimit = WEEK_ALL_DAY_ROWS
        )
    } else {
        GridOptions()
    }

/** A vertical scroll that starts at the first hour people look at, not at midnight. */
@Composable
private fun rememberFirstHourScroll(): ScrollState {
    val metrics = rememberGridMetrics()
    val density = LocalDensity.current
    val start = with(density) {
        metrics.scale.offsetOf(FIRST_HOUR_SHOWN * TimeScale.MINUTES_PER_HOUR).dp.roundToPx()
    }
    return rememberScrollState(start)
}
