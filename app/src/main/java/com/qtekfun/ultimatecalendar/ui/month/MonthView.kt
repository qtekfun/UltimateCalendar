// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.month

import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.domain.month.MonthPages
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.ui.shell.ShellActions
import com.qtekfun.ultimatecalendar.ui.shell.ShellUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The Month view inside the shell, fed by [MonthViewModel] (RF-03). */
@Composable
fun MonthScreen(
    state: ShellUiState,
    actions: ShellActions,
    modifier: Modifier = Modifier,
    viewModel: MonthViewModel = viewModel()
) {
    val source = remember(viewModel) {
        object : MonthPageSource {
            override fun initial(month: YearMonth, firstDayOfWeek: DayOfWeek) =
                viewModel.emptyPage(month, firstDayOfWeek)

            override fun page(month: YearMonth, firstDayOfWeek: DayOfWeek) =
                viewModel.page(month, firstDayOfWeek)
        }
    }
    val callbacks = remember(actions, viewModel) {
        MonthCallbacks(
            onOpenEvent = actions.onOpenEvent,
            onOpenDay = { day ->
                actions.onSelectDate(day)
                actions.onSelectView(CalendarView.DAY)
            },
            onCreateAt = { day -> actions.onCreateAt(viewModel.quickCreateAt(day)) }
        )
    }
    MonthView(state, source, viewModel.zone(), callbacks, actions.onSelectDate, modifier)
}

/**
 * A vertical, swipeable run of month pages. The shell's selected date is followed by the pager
 * (Today, the date picker) and the date of the month it settles on after a swipe is reported
 * through [onSelectDate]. Each page reads its own month once and keeps its layout while it is on
 * screen.
 */
@Composable
internal fun MonthView(
    state: ShellUiState,
    source: MonthPageSource,
    zone: ZoneId,
    callbacks: MonthCallbacks,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val date = state.date
    val firstDay = state.firstDayOfWeek
    var anchor by remember { mutableStateOf(date) }
    val pages = remember(anchor) { MonthPages(anchor) }
    val selected by rememberUpdatedState(date)
    var sheet by remember { mutableStateOf<DaySheet?>(null) }
    val pageCallbacks = remember(callbacks) { callbacks.copy(onShowDay = { sheet = it }) }
    val context = remember(state.today, firstDay, state.showWeekNumbers, zone) {
        MonthContext(state.today, firstDay, state.showWeekNumbers, zone)
    }
    key(pages) {
        val pager = rememberPagerState(pages.pageOf(date) ?: MonthPages.CENTER) {
            MonthPages.COUNT
        }
        LaunchedEffect(date) {
            val target = pages.pageOf(date)
            when {
                target == null -> anchor = date
                target != pager.currentPage -> pager.animateScrollToPage(target)
            }
        }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { page ->
                if (pages.monthAt(page) != YearMonth.from(selected)) {
                    onSelectDate(pages.dateAt(page, selected))
                }
            }
        }
        VerticalPager(pager, modifier) { page ->
            val month = pages.monthAt(page)
            val content by remember(month, firstDay) { source.page(month, firstDay) }
                .collectAsStateWithLifecycle(
                    remember(month, firstDay) { source.initial(month, firstDay) }
                )
            MonthPageContent(content.page, content.failed, context, pageCallbacks)
        }
    }
    sheet?.let { open ->
        MonthDaySheet(
            sheet = open,
            zone = zone,
            onOpenEvent = {
                sheet = null
                callbacks.onOpenEvent(it)
            },
            onOpenDay = {
                sheet = null
                callbacks.onOpenDay(open.date)
            },
            onDismiss = { sheet = null }
        )
    }
}
