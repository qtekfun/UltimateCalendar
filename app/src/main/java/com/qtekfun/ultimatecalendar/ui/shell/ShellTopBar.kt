// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.navigation.WeekNumbers
import com.qtekfun.ultimatecalendar.ui.components.CalendarTopBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Month and year of the selected date, as in Google Calendar's header. */
internal fun monthTitle(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("LLLL y", locale))
        .replaceFirstChar { it.titlecase(locale) }

/** The shell's header: [CalendarTopBar] fed from [state], plus the date picker it opens. */
@Composable
fun ShellTopBar(
    state: ShellUiState,
    actions: ShellActions,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    showMenu: Boolean = true
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val title = monthTitle(state.date, androidx.compose.ui.text.intl.Locale.current.platformLocale)
    val week = if (state.showWeekNumbers) {
        stringResource(
            R.string.shell_week_number,
            WeekNumbers.of(state.date, state.firstDayOfWeek)
        )
    } else {
        null
    }
    CalendarTopBar(
        title = title,
        subtitle = week,
        today = state.today,
        view = state.view,
        pendingInvitations = state.pendingInvitations,
        pickerOpen = picking,
        onOpenDrawer = onOpenDrawer,
        showMenu = showMenu,
        onTitleClick = { picking = true },
        onSelectView = actions.onSelectView,
        onToday = actions.onToday,
        onSearch = actions.onSearch,
        onInvitations = actions.onInvitations,
        modifier = modifier
    )
    if (picking) {
        DateDialog(
            date = state.date,
            onPicked = {
                picking = false
                actions.onSelectDate(it)
            },
            onDismiss = { picking = false }
        )
    }
}

@Composable
private fun DateDialog(date: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker works in UTC days.
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = remember(date) {
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = picker.selectedDateMillis
                    if (millis == null) {
                        onDismiss()
                    } else {
                        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                }
            ) { Text(stringResource(R.string.shell_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shell_cancel)) }
        }
    ) {
        DatePicker(state = picker)
    }
}
