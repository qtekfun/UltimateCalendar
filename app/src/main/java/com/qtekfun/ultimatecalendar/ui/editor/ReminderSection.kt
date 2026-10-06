// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.editor.ReminderInput
import com.qtekfun.ultimatecalendar.domain.editor.ReminderUnit
import com.qtekfun.ultimatecalendar.domain.editor.withReminder
import com.qtekfun.ultimatecalendar.domain.editor.withoutReminder
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/** The event's reminders, each removable, a menu to add another and the all-day ring time. */
@Composable
internal fun ReminderSection(
    form: EventForm,
    allDayMinute: Int,
    onChange: ((EventForm) -> EventForm) -> Unit
) {
    form.reminders.forEachIndexed { index, reminder ->
        val text = reminderText(reminder, form.allDay)
        EditorRow(if (index == 0) Icons.Filled.Notifications else null) {
            RowText(text)
            IconButton(onClick = { onChange { it.withoutReminder(reminder) } }) {
                Icon(Icons.Filled.Close, stringResource(R.string.editor_remove_reminder, text))
            }
        }
    }
    if (form.reminders.size < SettingsRules.MAX_REMINDERS) {
        AddReminder(form, hasIcon = form.reminders.isEmpty()) { reminder ->
            onChange { it.withReminder(reminder) }
        }
    } else {
        ProblemText(stringResource(R.string.editor_reminders_full))
    }
    if (form.allDay && form.reminders.isNotEmpty()) {
        Text(
            stringResource(R.string.editor_reminder_all_day_time, formatTimeOfDay(allDayMinute)),
            modifier = Modifier.padding(
                start = Spacing.xxl + Spacing.xl,
                end = Spacing.l,
                bottom = Spacing.xs
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddReminder(form: EventForm, hasIcon: Boolean, onAdd: (Reminder) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    val free = ReminderInput.choices(form.allDay).map { Reminder(it) }.filterNot {
        it in
            form.reminders
    }
    EditorRow(
        if (hasIcon) Icons.Filled.Notifications else null,
        onClick = { menu = true },
        description = stringResource(R.string.editor_add_reminder)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        RowText(stringResource(R.string.editor_add_reminder), Modifier.padding(start = Spacing.s))
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            free.forEach { reminder ->
                DropdownMenuItem(
                    text = { Text(reminderText(reminder, form.allDay)) },
                    onClick = {
                        menu = false
                        onAdd(reminder)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_reminder_custom)) },
                onClick = {
                    menu = false
                    custom = true
                }
            )
        }
    }
    if (custom) {
        CustomReminderDialog(form.allDay, onAdd = onAdd, onDismiss = { custom = false })
    }
}

/** "[number] [minutes | hours | days | weeks] before". */
@Composable
private fun CustomReminderDialog(
    allDay: Boolean,
    onAdd: (Reminder) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(ReminderInput.units(allDay).first()) }
    val minutes = text.toIntOrNull()?.let { ReminderInput.minutes(it, unit) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_reminder_custom_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                    label = { Text(stringResource(R.string.editor_reminder_amount)) },
                    isError = minutes == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Column(Modifier.selectableGroup().padding(top = Spacing.s)) {
                    ReminderInput.units(allDay).forEach { option ->
                        UnitRow(option, option == unit) { unit = option }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = minutes != null,
                onClick = {
                    minutes?.let { onAdd(Reminder(it)) }
                    onDismiss()
                }
            ) { Text(stringResource(R.string.editor_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        }
    )
}

private const val MAX_DIGITS = 5

@Composable
private fun UnitRow(unit: ReminderUnit, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(end = Spacing.m)
        )
        Text(
            stringResource(
                when (unit) {
                    ReminderUnit.MINUTES -> R.string.editor_unit_minutes
                    ReminderUnit.HOURS -> R.string.editor_unit_hours
                    ReminderUnit.DAYS -> R.string.editor_unit_days
                    ReminderUnit.WEEKS -> R.string.editor_unit_weeks
                }
            )
        )
    }
}
