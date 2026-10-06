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
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.layout.NavigationStyle
import com.qtekfun.ultimatecalendar.domain.layout.WidthClass
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.domain.navigation.ViewPeriods
import com.qtekfun.ultimatecalendar.ui.adaptive.currentAdaptiveLayout
import com.qtekfun.ultimatecalendar.ui.agenda.AgendaMasterDetail
import com.qtekfun.ultimatecalendar.ui.agenda.AgendaScreen
import com.qtekfun.ultimatecalendar.ui.components.AnimatedPeriod
import com.qtekfun.ultimatecalendar.ui.components.CalendarSnackbarHost
import com.qtekfun.ultimatecalendar.ui.components.CreateFab
import com.qtekfun.ultimatecalendar.ui.components.LocalCalendarNames
import com.qtekfun.ultimatecalendar.ui.components.LocalSnackbarHost
import com.qtekfun.ultimatecalendar.ui.components.PeriodKey
import com.qtekfun.ultimatecalendar.ui.components.rememberFabScrollState
import com.qtekfun.ultimatecalendar.ui.month.MonthScreen
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
fun ShellScreen(
    navigation: ShellActions,
    viewModel: ShellViewModel = viewModel(),
    detailPane: DetailPane? = null,
    looks: CalendarLookViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShellContent(
        state,
        navigation.copy(
            onSelectView = {
                viewModel.selectView(it)
                navigation.onSelectView(it)
            },
            onSelectDate = viewModel::selectDate,
            onToday = viewModel::goToToday,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onSetCalendarVisible = viewModel::setCalendarVisible,
            onCalendarPermissionAnswered = viewModel::calendarPermissionAnswered,
            onSaveCalendarLook = looks::save
        ),
        detailPane = detailPane
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
    detailPane: DetailPane? = null,
    content: @Composable (PeriodKey, PaddingValues) -> Unit = { period, padding ->
        // While a view fades into another both are drawn: each gets its own view in the state, or
        // a week grid told it is the agenda would fail (it only pages whole days).
        val shown = if (period.view == state.view) state else state.copy(view = period.view)
        ShellView(period.view, shown, actions, detailPane, Modifier.fillMaxSize().padding(padding))
    }
) {
    val drawer =
        rememberDrawerState(if (startWithDrawerOpen) DrawerValue.Open else DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val layout = currentAdaptiveLayout()
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
    val onClose: () -> Unit = { scope.launch { drawer.close() } }
    val drawerContent = @Composable { permanent: Boolean ->
        ShellDrawer(
            state,
            actions,
            onClose = onClose,
            permanent = permanent,
            modifier = Modifier
                .graphicsLayer { translationX = -BACK_SLIDE_PX * backPush.floatValue }
                .alpha(1f - BACK_FADE * backPush.floatValue)
        )
    }
    val scaffold = @Composable {
        ShellScaffold(
            state,
            actions,
            snackbarHost,
            onOpenDrawer = { scope.launch { drawer.open() } },
            content = content
        )
    }
    val names = remember(state.accounts) {
        state.accounts.flatMap { it.calendars }.associate { it.id to it.displayName }
    }
    CompositionLocalProvider(
        LocalSnackbarHost provides snackbarHost,
        LocalCalendarNames provides names
    ) {
        when (layout.navigation) {
            NavigationStyle.PERMANENT_DRAWER -> PermanentNavigationDrawer(
                drawerContent = { drawerContent(true) },
                modifier = modifier,
                content = scaffold
            )

            NavigationStyle.MODAL_DRAWER -> ModalNavigationDrawer(
                modifier = modifier,
                drawerState = drawer,
                drawerContent = { drawerContent(false) },
                content = scaffold
            )
        }
    }
}

/** The header, the Create button and the current view, with the room each window gives it. */
@Composable
private fun ShellScaffold(
    state: ShellUiState,
    actions: ShellActions,
    snackbarHost: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    content: @Composable (PeriodKey, PaddingValues) -> Unit
) {
    val layout = currentAdaptiveLayout()
    val fab = rememberFabScrollState()
    val wide = layout.widthClass != WidthClass.COMPACT
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                ShellTopBar(
                    state,
                    actions,
                    onOpenDrawer = onOpenDrawer,
                    showMenu = layout.navigation == NavigationStyle.MODAL_DRAWER
                )
                if (state.calendarPermissionMissing) {
                    CalendarPermissionBanner(onResult = actions.onCalendarPermissionAnswered)
                }
            }
        },
        floatingActionButton = {
            CreateFab(expanded = fab.expanded || wide, onClick = actions.onNewEvent)
        },
        snackbarHost = { CalendarSnackbarHost(snackbarHost) }
    ) { padding ->
        Box(Modifier.fillMaxSize().nestedScroll(fab.connection), Alignment.TopCenter) {
            val anchor = if (state.view in PAGED_VIEWS) LocalDate.ofEpochDay(0) else state.date
            AnimatedPeriod(
                PeriodKey(state.view, anchor),
                Modifier
                    .widthIn(max = layout.contentMaxWidthDp(state.view)?.dp ?: Dp.Unspecified)
                    .fillMaxSize()
            ) { period -> content(period, padding) }
        }
    }
}

/**
 * The view the shell shows. On a wide window the Agenda has the event detail beside it
 * ([detailPane]); everywhere else the detail opens full screen (see `AppNavigation`).
 */
@Composable
private fun ShellView(
    view: CalendarView,
    state: ShellUiState,
    actions: ShellActions,
    detailPane: DetailPane?,
    modifier: Modifier
) {
    when (view) {
        CalendarView.DAY, CalendarView.THREE_DAYS, CalendarView.WEEK ->
            TimeGridScreen(state, actions, modifier)

        CalendarView.AGENDA -> if (detailPane != null && currentAdaptiveLayout().agendaTwoPane) {
            AgendaMasterDetail(
                selected = detailPane.selected,
                list = { AgendaScreen(state, actions, it, detailPane.selected) },
                detail = detailPane.content,
                modifier = modifier
            )
        } else {
            AgendaScreen(state, actions, modifier)
        }

        CalendarView.MONTH -> MonthScreen(state, actions, modifier)
    }
}

/** Views that move through their own days: the shell only fades when they are chosen. */
private val PAGED_VIEWS = setOf(
    CalendarView.DAY,
    CalendarView.THREE_DAYS,
    CalendarView.WEEK,
    CalendarView.AGENDA,
    CalendarView.MONTH
)

private const val BACK_SLIDE_PX = 48f
private const val BACK_FADE = 0.3f
