// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaItem
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaItems
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.ui.components.EmptyState
import com.qtekfun.ultimatecalendar.ui.components.EventListSkeleton
import com.qtekfun.ultimatecalendar.ui.shell.ShellActions
import com.qtekfun.ultimatecalendar.ui.shell.ShellUiState
import com.qtekfun.ultimatecalendar.ui.theme.rememberReduceMotion
import java.time.LocalDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** Rows from an edge of the list at which the next chunk of days starts loading. */
private const val EDGE_ROWS = 4

/** What the agenda asks of its owner. */
internal data class AgendaCallbacks(
    val onOpenEvent: (EventInstance) -> Unit = {},
    /** The day the user scrolled to, once the list settles: the shell's header follows it. */
    val onSelectDate: (LocalDate) -> Unit = {},
    val onShow: (LocalDate) -> Unit = {},
    val onEarlier: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onNewEvent: () -> Unit = {}
)

/** The Agenda inside the shell, fed by [AgendaViewModel] (RF-03). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    state: ShellUiState,
    actions: ShellActions,
    modifier: Modifier = Modifier,
    selected: EventRef? = null,
    viewModel: AgendaViewModel = viewModel()
) {
    val agenda by viewModel.state.collectAsStateWithLifecycle()
    // Pulling the list down refreshes everything, as the header's button does.
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = actions.onRefresh,
        modifier = modifier
    ) {
        AgendaView(
            agenda = agenda,
            date = state.date,
            today = state.today,
            callbacks = AgendaCallbacks(
                onOpenEvent = actions.onOpenEvent,
                onSelectDate = actions.onSelectDate,
                onShow = viewModel::show,
                onEarlier = viewModel::earlier,
                onLater = viewModel::later,
                onNewEvent = actions.onNewEvent
            ),
            selected = selected
        )
    }
}

/**
 * A continuous list of days with events. [date] is the shell's selected date: when it changes
 * from outside (Today, the date picker) the list scrolls to it, or reloads around it when it is
 * out of the loaded window; when the user scrolls, the day the list settles on is reported
 * through [AgendaCallbacks.onSelectDate].
 */
@Composable
internal fun AgendaView(
    agenda: AgendaState,
    date: LocalDate,
    today: LocalDate,
    callbacks: AgendaCallbacks,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    selected: EventRef? = null
) {
    val list = rememberLazyListState()
    val latest by rememberUpdatedState(agenda)
    // The generation whose anchor the list has been scrolled to: until then, the skeleton covers it.
    var positioned by remember { mutableIntStateOf(NOT_POSITIONED) }
    var reported by remember { mutableStateOf<LocalDate?>(null) }
    val ready = agenda.status != AgendaStatus.LOADING && positioned == agenda.generation

    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(date) {
        if (date == reported) return@LaunchedEffect
        reported = date
        val current = latest
        if (current.status == AgendaStatus.READY &&
            current.generation == positioned &&
            date in current.range
        ) {
            val index = AgendaItems.scrollIndex(current.items, date)
            if (reduceMotion) list.scrollToItem(index) else list.animateScrollToItem(index)
        } else {
            callbacks.onShow(date)
        }
    }
    LaunchedEffect(agenda.generation, agenda.status) {
        if (agenda.status != AgendaStatus.LOADING && positioned != agenda.generation) {
            list.scrollToItem(AgendaItems.scrollIndex(agenda.items, agenda.anchor))
            positioned = agenda.generation
        }
    }
    val reportScrolledDay = rememberUpdatedState { day: LocalDate ->
        reported = day
        callbacks.onSelectDate(day)
    }
    LaunchedEffect(list, positioned) {
        // Only what the user scrolled to counts: not the position the list was placed at.
        snapshotFlow { list.isScrollInProgress }.first { it }
        snapshotFlow { if (list.isScrollInProgress) null else firstVisibleDay(list, latest) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { reportScrolledDay.value(it) }
    }
    LoadMoreAtEdges(list, agenda, ready, callbacks)

    Box(modifier.fillMaxSize(), Alignment.Center) {
        if (ready) AgendaNotice(agenda, callbacks.onNewEvent, Modifier.padding(contentPadding))
        AgendaList(list, agenda, today, callbacks.onOpenEvent, Modifier, contentPadding, selected)
        if (!ready) {
            EventListSkeleton(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            )
        }
    }
}

private const val NOT_POSITIONED = -1

/** What replaces the list when it has nothing to show: the events could not be read, or none. */
@Composable
private fun AgendaNotice(agenda: AgendaState, onNewEvent: () -> Unit, modifier: Modifier) {
    when {
        agenda.status == AgendaStatus.FAILED -> EmptyState(
            title = stringResource(R.string.agenda_failed_title),
            body = stringResource(R.string.agenda_failed_body),
            modifier = modifier
        )

        agenda.items.isEmpty() -> EmptyState(
            title = stringResource(R.string.cal_empty_title),
            body = stringResource(R.string.cal_empty_body),
            modifier = modifier,
            actionLabel = stringResource(R.string.shell_create),
            onAction = onNewEvent
        )
    }
}

@Composable
internal fun AgendaList(
    list: LazyListState,
    agenda: AgendaState,
    today: LocalDate,
    onOpenEvent: (EventInstance) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    selected: EventRef? = null
) {
    LazyColumn(modifier.fillMaxSize(), list, contentPadding) {
        agenda.items.forEach { item ->
            when (item) {
                is AgendaItem.MonthDivider -> item(item.key, CONTENT_MONTH) {
                    AgendaMonthDivider(item.month)
                }

                is AgendaItem.DayHeader -> stickyHeader(item.key, CONTENT_DAY) {
                    AgendaDayHeader(item.date, today)
                }

                is AgendaItem.EventRow -> item(item.key, CONTENT_EVENT) {
                    AgendaEventRow(
                        item.entry,
                        agenda.zone,
                        onOpenEvent,
                        selected = selected != null && EventRef.of(item.entry.instance) == selected
                    )
                }
            }
        }
    }
}

private const val CONTENT_MONTH = "month"
private const val CONTENT_DAY = "day"
private const val CONTENT_EVENT = "event"

/** Asks for another chunk of days when the list is near its first or last row. */
@Composable
private fun LoadMoreAtEdges(
    list: LazyListState,
    agenda: AgendaState,
    ready: Boolean,
    callbacks: AgendaCallbacks
) {
    val nearStart by remember(list) { derivedStateOf { list.firstVisibleItemIndex < EDGE_ROWS } }
    val nearEnd by remember(list, agenda.items.size) {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= agenda.items.size - EDGE_ROWS
        }
    }
    // Keyed by the window too: when a chunk arrives and the list is still at an edge, ask again.
    LaunchedEffect(nearStart, ready, agenda.range) {
        if (ready && agenda.status == AgendaStatus.READY && nearStart) callbacks.onEarlier()
    }
    LaunchedEffect(nearEnd, ready, agenda.range) {
        if (ready && agenda.status == AgendaStatus.READY && nearEnd) callbacks.onLater()
    }
}

private fun firstVisibleDay(list: LazyListState, agenda: AgendaState): LocalDate? {
    val first = list.layoutInfo.visibleItemsInfo.firstOrNull() ?: return null
    return AgendaItems.dateAt(agenda.items, first.index)
}
