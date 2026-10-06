// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.ui.shell.label
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Motion
import com.qtekfun.ultimatecalendar.ui.theme.calendarType
import java.time.LocalDate

private const val MAX_BADGE = 99
private const val FLIPPED = 180f

/**
 * The header of the calendar: menu, "October 2026 v" (opens the date picker; the arrow flips
 * while [pickerOpen]), an optional [subtitle] such as the week number, the view switcher, the
 * Today button with today's number, search and the invitations tray with its count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun CalendarTopBar(
    title: String,
    today: LocalDate,
    view: CalendarView,
    pendingInvitations: Int,
    onOpenDrawer: () -> Unit,
    onTitleClick: () -> Unit,
    onSelectView: (CalendarView) -> Unit,
    onToday: () -> Unit,
    onSearch: () -> Unit,
    onInvitations: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    pickerOpen: Boolean = false,
    showMenu: Boolean = true
) {
    val pickDescription = stringResource(R.string.shell_pick_date, title)
    val arrow by animateFloatAsState(
        if (pickerOpen) FLIPPED else 0f,
        tween(Motion.SHORT_MS),
        label = "titleArrow"
    )
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.surface),
        navigationIcon = {
            // The permanent drawer of wide windows is always open: nothing to open.
            if (showMenu) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.shell_open_drawer)
                    )
                }
            }
        },
        title = { TitleButton(title, subtitle, pickDescription, arrow, onTitleClick) },
        actions = {
            ViewSwitcher(view, onSelectView)
            TodayButton(today, onToday)
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.shell_search)
                )
            }
            InvitationsTray(pendingInvitations, onInvitations)
        }
    )
}

/** The glyph of the current view; opens a menu with every view, the current one ticked. */
@Composable
fun ViewSwitcher(
    view: CalendarView,
    onSelect: (CalendarView) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val name = stringResource(view.label())
    Column(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                CalendarIcons.of(view),
                contentDescription = stringResource(R.string.shell_switch_view, name)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CalendarView.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.label())) },
                    leadingIcon = { Icon(CalendarIcons.of(entry), contentDescription = null) },
                    trailingIcon = {
                        if (entry == view) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    modifier = Modifier.semantics { if (entry == view) contentDescription = name },
                    onClick = {
                        open = false
                        onSelect(entry)
                    }
                )
            }
        }
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
                    Badge(Modifier.clearAndSetSemantics {}) {
                        Text(if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString())
                    }
                }
            }
        ) {
            Icon(Icons.Filled.Email, contentDescription = description)
        }
    }
}

@ComponentPreviews
@Composable
internal fun CalendarTopBarPreview() {
    PreviewSurface {
        Column {
            CalendarTopBar(
                title = "October 2026",
                today = PreviewToday,
                view = CalendarView.WEEK,
                pendingInvitations = 3,
                onOpenDrawer = {},
                onTitleClick = {},
                onSelectView = {},
                onToday = {},
                onSearch = {},
                onInvitations = {},
                subtitle = "Week 41"
            )
        }
    }
}

private const val TITLE_MAX_FONT_SCALE = 1.3f

@Composable
private fun TitleButton(
    title: String,
    subtitle: String?,
    description: String,
    arrowDegrees: Float,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .heightIn(min = Dimens.minTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The bar is one 64 dp row shared with up to five 48 dp buttons, so the title stops growing
        // at 130 %; the screen reader still says all of it and the period is repeated in the view.
        val density = LocalDensity.current
        val capped = remember(density) {
            Density(density.density, minOf(density.fontScale, TITLE_MAX_FONT_SCALE))
        }
        CompositionLocalProvider(LocalDensity provides capped) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    title,
                    style = MaterialTheme.calendarType.monthTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.calendarType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.rotate(arrowDegrees))
    }
}
