// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.editor.withCalendar
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.ui.components.CalendarColorDot
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/** A borderless text field with a leading icon, as Google Calendar's editor draws them. */
@Composable
internal fun EditorTextField(
    icon: ImageVector?,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    singleLine: Boolean
) {
    EditorRow(icon) {
        TextField(
            value = value,
            onValueChange = onChange,
            label = { Text(hint) },
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun LocationAndDescription(form: EventForm, onChange: ((EventForm) -> EventForm) -> Unit) {
    EditorTextField(
        Icons.Filled.Place,
        form.location,
        stringResource(R.string.editor_location),
        { text -> onChange { it.copy(location = text) } },
        singleLine = true
    )
    EditorTextField(
        Icons.Filled.Create,
        form.description,
        stringResource(R.string.editor_description),
        { text -> onChange { it.copy(description = text) } },
        singleLine = false
    )
}

/** The calendar, event color and availability rows. */
@Composable
internal fun CalendarSection(
    state: EditorUiState.Ready,
    onChange: ((EventForm) -> EventForm) -> Unit
) {
    val form = state.form
    CalendarRow(state) { calendar -> onChange { it.withCalendar(calendar) } }
    if (state.supportsColor) ColorRow(form) { color -> onChange { it.copy(color = color) } }
    AvailabilityRow(form.availability) { value -> onChange { it.copy(availability = value) } }
}

@Composable
private fun AvailabilityRow(current: Availability, onPick: (Availability) -> Unit) {
    val options = listOf(Availability.BUSY, Availability.FREE)
    val name = availabilityName(current)
    ChoiceMenuRow(
        description = stringResource(R.string.editor_availability, name),
        text = name,
        options = options,
        optionName = { availabilityName(it) },
        onPick = onPick
    )
}
