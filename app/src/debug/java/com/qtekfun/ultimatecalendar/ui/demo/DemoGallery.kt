// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.ui.components.AllDayChip
import com.qtekfun.ultimatecalendar.ui.components.CalendarBottomSheet
import com.qtekfun.ultimatecalendar.ui.components.CalendarCheckRow
import com.qtekfun.ultimatecalendar.ui.components.CalendarColorDot
import com.qtekfun.ultimatecalendar.ui.components.CalendarSnackbarHost
import com.qtekfun.ultimatecalendar.ui.components.CalendarTopBar
import com.qtekfun.ultimatecalendar.ui.components.CreateFab
import com.qtekfun.ultimatecalendar.ui.components.DayBadge
import com.qtekfun.ultimatecalendar.ui.components.DayBadgeState
import com.qtekfun.ultimatecalendar.ui.components.EmptyState
import com.qtekfun.ultimatecalendar.ui.components.EventChip
import com.qtekfun.ultimatecalendar.ui.components.EventListSkeleton
import com.qtekfun.ultimatecalendar.ui.components.SectionHeader
import com.qtekfun.ultimatecalendar.ui.components.showUndo
import com.qtekfun.ultimatecalendar.ui.theme.EventDisplay
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Every shared component on one scrolling page, for checking light, dark and large font. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DemoGallery() {
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(true) }
    val today = LocalDate.now()
    Scaffold(
        topBar = {
            CalendarTopBar(
                title = "October 2026",
                subtitle = "Week 41",
                today = today,
                view = CalendarView.WEEK,
                pendingInvitations = 3,
                onOpenDrawer = {},
                onTitleClick = {},
                onSelectView = {},
                onToday = {},
                onSearch = {},
                onInvitations = {}
            )
        },
        floatingActionButton = { CreateFab(expanded = true, onClick = {}) },
        snackbarHost = { CalendarSnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            SectionHeader("Day badges")
            Row(Modifier.padding(horizontal = Spacing.l)) {
                DayBadgeState.entries.forEach { DayBadge(today, state = it, onClick = {}) }
            }
            SectionHeader("Event chips")
            Column(
                Modifier.padding(horizontal = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                DemoChips()
            }
            CalendarsSection(checked, { checked = it }, snackbar) { sheet = true }
            SectionHeader("Empty state")
            EmptyState(
                title = stringResource(R.string.cal_empty_title),
                body = stringResource(R.string.cal_empty_body),
                actionLabel = stringResource(R.string.shell_create)
            )
            SectionHeader("Loading")
            EventListSkeleton()
        }
    }
    if (sheet) {
        CalendarBottomSheet(onDismiss = { sheet = false }) {
            SectionHeader("Add to calendar")
            DemoData.calendars.take(3).forEach {
                CalendarCheckRow(it.displayName, it.color, true, {})
            }
        }
    }
}

@Composable
private fun DemoChips() {
    val blue = DemoData.personal.color
    EventChip("Team standup", DemoData.work.color, detail = "9:00 - 9:30 AM")
    EventChip("Design review with a very long title that wraps", blue, detail = "10:00 AM")
    EventChip(
        "Quarterly sync (invitation)",
        DemoData.family.color,
        detail = "Not answered",
        display = EventDisplay.of(AttendeeStatus.NEEDS_ACTION)
    )
    EventChip(
        "Lunch (maybe)",
        DemoData.gym.color,
        detail = "12:30 PM",
        display = EventDisplay.of(AttendeeStatus.TENTATIVE)
    )
    EventChip(
        "Coffee (declined)",
        blue,
        detail = "3:00 PM",
        display = EventDisplay.of(AttendeeStatus.DECLINED)
    )
    EventChip("Birthday (light color)", DemoData.birthdays.color, detail = "All of the contrast")
    AllDayChip("Public holiday", DemoData.holidays.color)
    AllDayChip("Trip (invitation)", blue, display = EventDisplay.of(AttendeeStatus.NEEDS_ACTION))
}

@Composable
private fun CalendarsSection(
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    snackbar: SnackbarHostState,
    onSheet: () -> Unit
) {
    val scope = rememberCoroutineScope()
    SectionHeader("Calendars")
    CalendarCheckRow("Personal", DemoData.personal.color, checked, onChecked)
    CalendarCheckRow("Birthdays", DemoData.birthdays.color, !checked, { onChecked(!it) })
    Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        DemoData.calendars.forEach { CalendarColorDot(it.color) }
    }
    Row {
        TextButton(onClick = {
            scope.launch {
                val undo = snackbar.showUndo("Event deleted", "Undo")
                if (undo) snackbar.showSnackbar("Restored")
            }
        }) { Text("Snackbar") }
        TextButton(onClick = onSheet) { Text("Bottom sheet") }
    }
}
