// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.EventColorChoice
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.ui.editor.ColorSwatch
import com.qtekfun.ultimatecalendar.ui.editor.colorName
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/**
 * The name and color of a calendar on this phone (RF-02): for the calendars the source does not
 * let the app rename, and for anyone who wants Work to be teal. Nothing is written to the source.
 * [onSave] gets the typed name and the picked color, null for the source's own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarLookDialog(
    calendar: CalendarInfo,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int?) -> Unit
) {
    var name by rememberSaveable(calendar.id.value) { mutableStateOf(calendar.displayName) }
    var color by rememberSaveable(calendar.id.value) { mutableStateOf<Int?>(calendar.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cal_look_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    stringResource(R.string.cal_look_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.cal_look_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ColorSwatch(
                        null,
                        color == null,
                        stringResource(R.string.cal_look_color_default)
                    ) { color = null }
                    EventColorChoice.entries.forEach { choice ->
                        ColorSwatch(choice.argb, color == choice.argb, colorName(choice)) {
                            color = choice.argb
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color) }) {
                Text(stringResource(R.string.cal_look_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
