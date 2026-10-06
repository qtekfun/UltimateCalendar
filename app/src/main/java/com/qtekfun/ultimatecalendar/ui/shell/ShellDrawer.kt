// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.navigation.AccountCalendars
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.ui.components.CalendarCheckRow
import com.qtekfun.ultimatecalendar.ui.components.CalendarIcons
import com.qtekfun.ultimatecalendar.ui.components.SectionHeader
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Motion
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

private val ItemPadding = Spacing.m
private const val COLLAPSED_ARROW = -90f

/**
 * The drawer (RF-02), laid out like Google Calendar's: the views, then the calendars of each
 * account (a collapsible section, a colored checkbox per calendar), then Settings and Help.
 * [onClose] runs after a choice that leaves the drawer.
 */
@Composable
fun ShellDrawer(
    state: ShellUiState,
    actions: ShellActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    permanent: Boolean = false
) {
    val sheet = modifier.widthIn(max = Dimens.drawerMaxWidth)
    if (permanent) {
        PermanentDrawerSheet(sheet) { DrawerItems(state, actions, onClose) }
    } else {
        ModalDrawerSheet(sheet) { DrawerItems(state, actions, onClose) }
    }
}

@Composable
private fun DrawerItems(state: ShellUiState, actions: ShellActions, onClose: () -> Unit) {
    LazyColumn {
        item {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = Spacing.xl, vertical = Spacing.l)
                    .semantics { heading() }
            )
        }
        items(CalendarView.entries) { view ->
            NavigationDrawerItem(
                icon = { Icon(CalendarIcons.of(view), contentDescription = null) },
                label = { Text(stringResource(view.label())) },
                selected = view == state.view,
                onClick = {
                    actions.onSelectView(view)
                    onClose()
                },
                modifier = Modifier.padding(horizontal = ItemPadding)
            )
        }
        item { Divider() }
        item { SectionHeader(stringResource(R.string.shell_calendars)) }
        when {
            state.calendarsFailed ->
                item { Message(stringResource(R.string.shell_calendars_failed)) }

            state.accounts.isEmpty() ->
                item { Message(stringResource(R.string.shell_calendars_none)) }

            else -> items(
                state.accounts,
                key = { "account:${it.account.type}:${it.account.name}" }
            ) { group -> AccountSection(group, actions) }
        }
        item { Divider() }
        item { Footer(actions, onClose) }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(vertical = Spacing.s, horizontal = Spacing.l))
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.s)
    )
}

/** One account: its name (tap to fold) and its calendars with colored checkboxes. */
@Composable
private fun AccountSection(group: AccountCalendars, actions: ShellActions) {
    val name = group.account.name
    var expanded by rememberSaveable(name, group.account.type) { mutableStateOf(true) }
    val arrow by animateFloatAsState(
        if (expanded) 0f else COLLAPSED_ARROW,
        tween(Motion.SHORT_MS),
        label = "accountArrow"
    )
    val action = stringResource(
        if (expanded) R.string.cal_account_collapse else R.string.cal_account_expand,
        name
    )
    val state = stringResource(
        if (expanded) R.string.cal_state_expanded else R.string.cal_state_collapsed
    )
    Column(Modifier.animateContentSize(tween(Motion.MEDIUM_MS))) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouch)
                .clickable(onClickLabel = action, role = Role.Button) { expanded = !expanded }
                .padding(horizontal = Spacing.xl)
                .semantics(mergeDescendants = true) { stateDescription = state },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, Modifier.rotate(arrow))
        }
        AnimatedVisibility(expanded) {
            Column {
                group.calendars.forEach { calendar ->
                    CalendarCheckRow(
                        name = calendar.displayName,
                        color = calendar.color,
                        checked = calendar.visible,
                        onCheckedChange = { actions.onSetCalendarVisible(calendar.id, it) },
                        modifier = Modifier.padding(horizontal = Spacing.s)
                    )
                }
            }
        }
    }
}

@Composable
private fun Footer(actions: ShellActions, onClose: () -> Unit) {
    Column(Modifier.padding(horizontal = ItemPadding)) {
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.shell_settings)) },
            selected = false,
            onClick = {
                onClose()
                actions.onSettings()
            }
        )
        NavigationDrawerItem(
            icon = { Icon(CalendarIcons.Help, contentDescription = null) },
            label = { Text(stringResource(R.string.shell_help)) },
            selected = false,
            onClick = {
                onClose()
                actions.onHelp()
            }
        )
    }
}
