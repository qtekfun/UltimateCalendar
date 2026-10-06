// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.domain.navigation.ViewPeriods
import com.qtekfun.ultimatecalendar.ui.agenda.AgendaScreen
import com.qtekfun.ultimatecalendar.ui.components.AnimatedPeriod
import com.qtekfun.ultimatecalendar.ui.components.CalendarSnackbarHost
import com.qtekfun.ultimatecalendar.ui.components.CreateFab
import com.qtekfun.ultimatecalendar.ui.components.PeriodKey
import com.qtekfun.ultimatecalendar.ui.components.WindowWidth
import com.qtekfun.ultimatecalendar.ui.components.currentWindowWidth
import com.qtekfun.ultimatecalendar.ui.components.rememberFabScrollState
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import com.qtekfun.ultimatecalendar.ui.timegrid.TimeGridScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/**
 * The app shell in Google Calendar's style: header, drawer, current view and Create button.
 * [navigation] carries the actions that leave the shell (search, editor, detail...); the shell
 * fills in the ones that change what it shows.
 */
@Composable
fun ShellScreen(navigation: ShellActions, viewModel: ShellViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShellContent(
        state,
        navigation.copy(
            onSelectView = viewModel::selectView,
            onSelectDate = viewModel::selectDate,
            onToday = viewModel::goToToday,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onSetCalendarVisible = viewModel::setCalendarVisible
        )
    )
}

/**
 * The shell around a view. [content] draws the period it is given (the view and the day it is
 * anchored on) and respects the padding (top bar, navigation bar); the shell animates between
 * periods, so during a transition [content] is shown twice, for the old and the new period.
 * Views that scroll should be nested-scroll children of the body so the Create button can
 * collapse: any `LazyColumn` or `verticalScroll` is.
 */
@Composable
fun ShellContent(
    state: ShellUiState,
    actions: ShellActions,
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
    startWithDrawerOpen: Boolean = false,
    content: @Composable (PeriodKey, PaddingValues) -> Unit = { period, padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (period.view) {
            CalendarView.DAY, CalendarView.THREE_DAYS -> TimeGridScreen(state, actions, modifier)

            CalendarView.AGENDA -> AgendaScreen(state, actions, modifier)

            // T16 and T17 replace this with the Week and Month views.
            else -> ViewPlaceholder(period, state.firstDayOfWeek, actions, modifier)
        }
    }
) {
    val drawer =
        rememberDrawerState(if (startWithDrawerOpen) DrawerValue.Open else DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val fab = rememberFabScrollState()
    val wide = currentWindowWidth() != WindowWidth.COMPACT
    val backPush = remember { mutableFloatStateOf(0f) }
    // System back: the drawer follows the gesture (a small slide and fade), then closes.
    PredictiveBackHandler(enabled = drawer.isOpen) { progress ->
        try {
            progress.collect { backPush.floatValue = it.progress }
            drawer.close()
        } finally {
            backPush.floatValue = 0f
        }
    }
    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawer,
        drawerContent = {
            ShellDrawer(
                state,
                actions,
                onClose = { scope.launch { drawer.close() } },
                modifier = Modifier
                    .graphicsLayer { translationX = -BACK_SLIDE_PX * backPush.floatValue }
                    .alpha(1f - BACK_FADE * backPush.floatValue)
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                ShellTopBar(state, actions, onOpenDrawer = { scope.launch { drawer.open() } })
            },
            floatingActionButton = {
                CreateFab(expanded = fab.expanded || wide, onClick = actions.onNewEvent)
            },
            snackbarHost = { CalendarSnackbarHost(snackbarHost) }
        ) { padding ->
            Box(Modifier.fillMaxSize().nestedScroll(fab.connection), Alignment.TopCenter) {
                AnimatedPeriod(
                    PeriodKey(
                        state.view,
                        if (state.view in
                            PAGED_VIEWS
                        ) {
                            LocalDate.ofEpochDay(0)
                        } else {
                            state.date
                        }
                    ),
                    Modifier.widthIn(max = Dimens.contentMaxWidth).fillMaxSize()
                ) { period -> content(period, padding) }
            }
        }
    }
}

/** Views that move through their own days: the shell only fades when they are chosen. */
private val PAGED_VIEWS = setOf(CalendarView.DAY, CalendarView.THREE_DAYS, CalendarView.AGENDA)

private const val BACK_SLIDE_PX = 48f
private const val BACK_FADE = 0.3f

/** Stands in for the views until T14-T17 land. */
@Composable
private fun ViewPlaceholder(
    period: PeriodKey,
    firstDayOfWeek: DayOfWeek,
    actions: ShellActions,
    modifier: Modifier
) {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val range = ViewPeriods.range(period.view, period.date, firstDayOfWeek)
    val last = range.endExclusive.minusDays(1)
    Column(
        modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(period.view.label()), style = MaterialTheme.typography.headlineMedium)
        Text("${range.start.format(format)} - ${last.format(format)}")
        Text(stringResource(R.string.shell_coming_soon))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            TextButton(onClick = actions.onPrevious) {
                Text(stringResource(R.string.shell_previous))
            }
            TextButton(onClick = actions.onNext) { Text(stringResource(R.string.shell_next)) }
        }
    }
}
