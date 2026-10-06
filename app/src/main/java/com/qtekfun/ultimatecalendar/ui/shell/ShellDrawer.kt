// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView

private val DrawerPadding = 28.dp

/**
 * The drawer (RF-02): the views, the calendars of each account with their color and a
 * visible/hidden checkbox, and Settings. [onClose] runs after a choice that leaves the drawer.
 */
@Composable
fun ShellDrawer(
    state: ShellUiState,
    actions: ShellActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier) {
        LazyColumn {
            item {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(DrawerPadding)
                )
            }
            items(CalendarView.entries) { view ->
                NavigationDrawerItem(
                    label = { Text(stringResource(view.label())) },
                    selected = view == state.view,
                    onClick = {
                        actions.onSelectView(view)
                        onClose()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle(stringResource(R.string.shell_calendars)) }
            calendars(state, actions)
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.shell_settings)) },
                    selected = false,
                    onClick = {
                        onClose()
                        actions.onSettings()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

private fun LazyListScope.calendars(state: ShellUiState, actions: ShellActions) {
    when {
        state.calendarsFailed -> item { Message(stringResource(R.string.shell_calendars_failed)) }

        state.accounts.isEmpty() -> item { Message(stringResource(R.string.shell_calendars_none)) }

        else -> state.accounts.forEach { group ->
            item(key = "account:${group.account.type}:${group.account.name}") {
                Text(
                    group.account.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = DrawerPadding, vertical = 8.dp)
                        .semantics { heading() }
                )
            }
            items(group.calendars, key = { it.id.value }) { calendar ->
                CalendarRow(calendar) { actions.onSetCalendarVisible(calendar.id, it) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .padding(horizontal = DrawerPadding, vertical = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = DrawerPadding, vertical = 8.dp)
    )
}

@Composable
private fun CalendarRow(calendar: CalendarInfo, onVisibleChange: (Boolean) -> Unit) {
    val color = Color(calendar.color)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = calendar.visible,
                role = Role.Checkbox,
                onValueChange = onVisibleChange
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = calendar.visible,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color)
        )
        Spacer(Modifier.width(16.dp))
        Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
    }
}
