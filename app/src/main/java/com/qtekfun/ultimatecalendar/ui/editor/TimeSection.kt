// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.editor.FormIssue
import com.qtekfun.ultimatecalendar.domain.editor.withAllDay
import com.qtekfun.ultimatecalendar.domain.editor.withEndDate
import com.qtekfun.ultimatecalendar.domain.editor.withEndTime
import com.qtekfun.ultimatecalendar.domain.editor.withStartDate
import com.qtekfun.ultimatecalendar.domain.editor.withStartTime
import com.qtekfun.ultimatecalendar.domain.editor.withZone
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.ZoneId

/** Which picker is open. */
private enum class Picker { START_DATE, START_TIME, END_DATE, END_TIME, ZONE }

/** The all-day switch, the start and end rows with their pickers, and the time zone. */
@Composable
internal fun TimeSection(
    form: EventForm,
    device: ZoneId,
    onChange: ((EventForm) -> EventForm) -> Unit
) {
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    AllDaySwitch(form.allDay) { value -> onChange { it.withAllDay(value) } }
    DateTimeRow(
        date = PickerField(
            formatDate(form.startDate),
            R.string.editor_start_date
        ) { picker = Picker.START_DATE },
        time = PickerField(
            formatTime(form.start.toLocalTime()),
            R.string.editor_start_time
        ) { picker = Picker.START_TIME }.takeUnless { form.allDay }
    )
    DateTimeRow(
        date = PickerField(formatDate(form.endDate), R.string.editor_end_date) {
            picker = Picker.END_DATE
        },
        time = PickerField(
            formatTime(form.end.toLocalTime()),
            R.string.editor_end_time
        ) { picker = Picker.END_TIME }.takeUnless { form.allDay }
    )
    if (FormIssue.END_BEFORE_START in form.issues) {
        ProblemText(issueText(FormIssue.END_BEFORE_START))
    }
    if (!form.allDay) {
        val label = stringResource(R.string.editor_zone, form.zone.id)
        EditorRow(Icons.Filled.Info, onClick = { picker = Picker.ZONE }, description = label) {
            RowText(form.zone.id.replace('_', ' '))
        }
    }
    OpenPicker(picker, form, device, onChange) { picker = null }
}

/** The dialog of the picker that is open, if any. */
@Composable
private fun OpenPicker(
    picker: Picker?,
    form: EventForm,
    device: ZoneId,
    onChange: ((EventForm) -> EventForm) -> Unit,
    onDismiss: () -> Unit
) {
    when (picker) {
        Picker.START_DATE -> EditorDatePicker(
            form.startDate,
            { date -> onChange { it.withStartDate(date) } },
            onDismiss
        )

        Picker.END_DATE -> EditorDatePicker(
            form.endDate,
            { date -> onChange { it.withEndDate(date) } },
            onDismiss
        )

        Picker.START_TIME -> EditorTimePicker(
            form.start.toLocalTime(),
            { time -> onChange { it.withStartTime(time) } },
            onDismiss
        )

        Picker.END_TIME -> EditorTimePicker(
            form.end.toLocalTime(),
            { time -> onChange { it.withEndTime(time) } },
            onDismiss
        )

        Picker.ZONE -> ZonePicker(
            current = form.zone,
            device = device,
            at = form.start.toInstant(),
            onPick = { zone -> onChange { it.withZone(zone) } },
            onDismiss = onDismiss
        )

        null -> Unit
    }
}

@Composable
private fun AllDaySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    EditorRow(
        Icons.Filled.DateRange,
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onChange
        )
    ) {
        RowText(stringResource(R.string.editor_all_day))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The text of a date or time button, how a screen reader names it, and what it does. */
private class PickerField(val text: String, val description: Int, val onClick: () -> Unit)

/** A date and, for timed events, a time: two 48 dp targets on one line. */
@Composable
private fun DateTimeRow(date: PickerField, time: PickerField?) {
    EditorRow(icon = null) {
        PickerText(date, Modifier.weight(1f))
        if (time != null) PickerText(time)
    }
}

@Composable
private fun PickerText(field: PickerField, modifier: Modifier = Modifier) {
    val description = stringResource(field.description, field.text)
    Box(
        modifier
            .heightIn(min = Dimens.minTouch)
            .clickable(role = Role.Button, onClick = field.onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            field.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = Spacing.s)
        )
    }
}
