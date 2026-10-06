// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.ui.timegrid.TimeGridScreen
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/**
 * The app shell in Google Calendar's style: header, drawer, current view and new-event button.
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

@Composable
fun ShellContent(state: ShellUiState, actions: ShellActions, modifier: Modifier = Modifier) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawer,
        drawerContent = {
            ShellDrawer(state, actions, onClose = { scope.launch { drawer.close() } })
        }
    ) {
        Scaffold(
            topBar = {
                ShellTopBar(state, actions, onOpenDrawer = { scope.launch { drawer.open() } })
            },
            floatingActionButton = {
                FloatingActionButton(onClick = actions.onNewEvent) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.shell_new_event)
                    )
                }
            }
        ) { padding ->
            val content = Modifier.fillMaxSize().padding(padding)
            when (state.view) {
                CalendarView.DAY, CalendarView.THREE_DAYS -> TimeGridScreen(state, actions, content)

                // T14, T16 and T17 replace this with the Agenda, Week and Month views.
                else -> ViewPlaceholder(state, actions, content)
            }
        }
    }
}

@Composable
private fun ViewPlaceholder(state: ShellUiState, actions: ShellActions, modifier: Modifier) {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val last = state.range.endExclusive.minusDays(1)
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(state.view.label()), style = MaterialTheme.typography.headlineMedium)
        Text("${state.range.start.format(format)} - ${last.format(format)}")
        Text(stringResource(R.string.shell_coming_soon))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = actions.onPrevious) {
                Text(stringResource(R.string.shell_previous))
            }
            TextButton(onClick = actions.onNext) { Text(stringResource(R.string.shell_next)) }
        }
    }
}
