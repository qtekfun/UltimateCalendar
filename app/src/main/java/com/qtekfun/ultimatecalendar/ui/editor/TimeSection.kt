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
        date = formatDate(form.startDate),
        time = formatTime(form.start.toLocalTime()).takeUnless { form.allDay },
        dateDescription = R.string.editor_start_date,
        timeDescription = R.string.editor_start_time,
        onDate = { picker = Picker.START_DATE },
        onTime = { picker = Picker.START_TIME }
    )
    DateTimeRow(
        date = formatDate(form.endDate),
        time = formatTime(form.end.toLocalTime()).takeUnless { form.allDay },
        dateDescription = R.string.editor_end_date,
        timeDescription = R.string.editor_end_time,
        onDate = { picker = Picker.END_DATE },
        onTime = { picker = Picker.END_TIME }
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
    when (picker) {
        Picker.START_DATE -> EditorDatePicker(
            form.startDate,
            { date -> onChange { it.withStartDate(date) } },
            { picker = null }
        )

        Picker.END_DATE -> EditorDatePicker(
            form.endDate,
            { date -> onChange { it.withEndDate(date) } },
            { picker = null }
        )

        Picker.START_TIME -> EditorTimePicker(
            form.start.toLocalTime(),
            { time -> onChange { it.withStartTime(time) } },
            { picker = null }
        )

        Picker.END_TIME -> EditorTimePicker(
            form.end.toLocalTime(),
            { time -> onChange { it.withEndTime(time) } },
            { picker = null }
        )

        Picker.ZONE -> ZonePicker(
            current = form.zone,
            device = device,
            at = form.start.toInstant(),
            onPick = { zone -> onChange { it.withZone(zone) } },
            onDismiss = { picker = null }
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

/** A date and, for timed events, a time: two 48 dp targets on one line. */
@Composable
private fun DateTimeRow(
    date: String,
    time: String?,
    dateDescription: Int,
    timeDescription: Int,
    onDate: () -> Unit,
    onTime: () -> Unit
) {
    EditorRow(icon = null) {
        PickerText(date, stringResource(dateDescription, date), onDate, Modifier.weight(1f))
        if (time != null) {
            PickerText(time, stringResource(timeDescription, time), onTime)
        }
    }
}

@Composable
private fun PickerText(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .heightIn(min = Dimens.minTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = Spacing.s)
        )
    }
}
