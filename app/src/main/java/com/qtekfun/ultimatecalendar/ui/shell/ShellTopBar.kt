// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_BADGE = 99

/** Month and year of the selected date, as in Google Calendar's header. */
internal fun monthTitle(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("LLLL y", locale))
        .replaceFirstChar { it.titlecase(locale) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellTopBar(
    state: ShellUiState,
    actions: ShellActions,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val title = monthTitle(state.date, androidx.compose.ui.text.intl.Locale.current.platformLocale)
    val pickDescription = stringResource(R.string.shell_pick_date, title)
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.shell_open_drawer)
                )
            }
        },
        title = {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { picking = true }
                    .semantics(mergeDescendants = true) { contentDescription = pickDescription },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        },
        actions = {
            TextButton(onClick = actions.onToday) { Text(stringResource(R.string.shell_today)) }
            IconButton(onClick = actions.onSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.shell_search)
                )
            }
            InvitationsTray(state.pendingInvitations, actions.onInvitations)
        }
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
private fun InvitationsTray(count: Int, onClick: () -> Unit) {
    val description = if (count > 0) {
        pluralStringResource(R.plurals.shell_invitations_pending, count, count)
    } else {
        stringResource(R.string.shell_invitations)
    }
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (count > 0) {
                    Badge { Text(if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString()) }
                }
            }
        ) {
            Icon(Icons.Filled.Email, contentDescription = description)
        }
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
