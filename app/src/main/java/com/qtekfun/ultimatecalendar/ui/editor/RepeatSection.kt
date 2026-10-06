// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.editor.FormIssue
import com.qtekfun.ultimatecalendar.domain.editor.RepeatSetting
import com.qtekfun.ultimatecalendar.domain.editor.withRepeat
import com.qtekfun.ultimatecalendar.domain.recurrence.CustomRepeat
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatPreset

/** The Repeat row: the presets in a menu, and "Custom…" for the rule editor. */
@Composable
internal fun RepeatSection(form: EventForm, onChange: ((EventForm) -> EventForm) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    val text = repeatText(form.repeat, form.startDate)
    EditorRow(
        Icons.Filled.Refresh,
        onClick = { menu = true },
        description = stringResource(R.string.editor_repeat, text)
    ) {
        RowText(text)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            RepeatPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(presetName(preset)) },
                    onClick = {
                        menu = false
                        onChange { it.withRepeat(RepeatSetting.Preset(preset)) }
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_repeat_custom)) },
                onClick = {
                    menu = false
                    custom = true
                }
            )
        }
    }
    if (FormIssue.REPEAT_ENDS_BEFORE_START in form.issues) {
        ProblemText(issueText(FormIssue.REPEAT_ENDS_BEFORE_START))
    }
    if (custom) {
        val start = (form.repeat as? RepeatSetting.Custom)?.repeat
            ?: CustomRepeat.from(null, form.startDate, form.zone)
        CustomRepeatDialog(
            initial = start,
            anchor = form.startDate,
            onDone = { repeat -> onChange { it.withRepeat(RepeatSetting.Custom(repeat)) } },
            onDismiss = { custom = false }
        )
    }
}
