// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.EventColorChoice
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.navigation.AccountCalendars
import com.qtekfun.ultimatecalendar.ui.components.CalendarBottomSheet
import com.qtekfun.ultimatecalendar.ui.components.CalendarColorDot
import com.qtekfun.ultimatecalendar.ui.components.SectionHeader
import com.qtekfun.ultimatecalendar.ui.theme.ColorMath
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/** A row that opens a small menu of [options]; the row says what is chosen now. */
@Composable
internal fun <T> ChoiceMenuRow(
    description: String,
    text: String,
    options: List<T>,
    optionName: @Composable (T) -> String,
    onPick: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    EditorRow(icon = null, onClick = { open = true }, description = description) {
        RowText(text)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionName(option)) },
                    onClick = {
                        open = false
                        onPick(option)
                    }
                )
            }
        }
    }
}

/**
 * The calendar the event is in: its color, name and account. A new event can be moved to any
 * calendar that accepts events, grouped by account; an existing one stays where it is.
 */
@Composable
internal fun CalendarRow(state: EditorUiState.Ready, onPick: (CalendarInfo) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val calendar = state.calendar
    val name = calendar?.displayName.orEmpty()
    EditorRow(
        icon = null,
        onClick = if (state.isNew) ({ open = true }) else null,
        description = stringResource(R.string.editor_calendar, name),
        leading = { calendar?.let { CalendarColorDot(it.color, size = Dimens.dotLarge) } }
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (state.isNew) {
                    calendar?.account?.name.orEmpty()
                } else {
                    stringResource(R.string.editor_calendar_locked)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (open) {
        CalendarBottomSheet(onDismiss = { open = false }) {
            CalendarChoices(state.calendars, state.form.calendarId?.value) {
                open = false
                onPick(it)
            }
        }
    }
}

@Composable
private fun CalendarChoices(calendars: List<CalendarInfo>, selected: Long?, onPick: (CalendarInfo) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        AccountCalendars.group(calendars).forEach { group ->
            SectionHeader(group.account.name)
            group.calendars.forEach { calendar ->
                CalendarChoiceRow(calendar, calendar.id.value == selected) { onPick(calendar) }
            }
        }
    }
}

@Composable
private fun CalendarChoiceRow(calendar: CalendarInfo, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = calendar.displayName }
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l)
    ) {
        CalendarColorDot(calendar.color, size = Dimens.dotLarge)
        Text(calendar.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) Icon(Icons.Filled.Check, contentDescription = null)
    }
}

/** The event color: the calendar's own, or one of the palette in a sheet. */
@Composable
internal fun ColorRow(form: EventForm, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val choice = EventColorChoice.entries.firstOrNull { it.argb == form.color }
    val name = choice?.let { colorName(it) } ?: stringResource(R.string.editor_color_default)
    EditorRow(
        icon = null,
        onClick = { open = true },
        description = stringResource(R.string.editor_color, name),
        leading = { ColorDot(form.color, Dimens.dotLarge) }
    ) { RowText(name) }
    if (open) {
        CalendarBottomSheet(onDismiss = { open = false }) {
            ColorChoices(form.color) {
                open = false
                onPick(it)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorChoices(selected: Int?, onPick: (Int?) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.l)) {
        Text(
            stringResource(R.string.editor_color_pick),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.s)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ColorSwatch(null, selected == null, stringResource(R.string.editor_color_default)) {
                onPick(null)
            }
            EventColorChoice.entries.forEach { choice ->
                ColorSwatch(choice.argb, selected == choice.argb, colorName(choice)) {
                    onPick(choice.argb)
                }
            }
        }
    }
}

/** A 48 dp target holding a color circle; ticked when it is the current one. */
@Composable
private fun ColorSwatch(color: Int?, selected: Boolean, name: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Dimens.minTouch)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        ColorDot(color, Dimens.minTouch - Spacing.s)
        if (selected && color != null) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color(ColorMath.onColor(color))
            )
        } else if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null)
        }
    }
}

/** A color circle; null (the calendar's color) is an empty ring. */
@Composable
private fun ColorDot(color: Int?, size: Dp) {
    if (color != null) {
        CalendarColorDot(color, size = size)
    } else {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    }
}
