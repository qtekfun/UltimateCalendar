// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules

/** A list of reminder offsets, each removable, and a menu to add another one. */
@Composable
internal fun RemindersEditor(title: String, offsets: List<Int>, onChange: (List<Int>) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        offsets.forEach { minutes ->
            val name = reminderName(minutes)
            RemovableRow(name, stringResource(R.string.settings_remove_reminder, name)) {
                onChange(offsets - minutes)
            }
        }
        if (offsets.size < SettingsRules.MAX_REMINDERS) {
            val free = SettingsRules.REMINDER_CHOICES - offsets.toSet()
            ChoiceRow(
                label = stringResource(R.string.settings_add_reminder),
                current = "",
                options = free,
                name = { reminderName(it) },
                onPick = { onChange(offsets + it) }
            )
        }
    }
}

/** The user's own email addresses: the list, and a field to add one. */
@Composable
internal fun OwnEmailsEditor(
    addresses: List<String>,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 8.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.settings_own_emails))
            Hint(stringResource(R.string.settings_own_emails_hint))
        }
        addresses.forEach { address ->
            RemovableRow(address, stringResource(R.string.settings_remove_email, address)) {
                onRemove(address)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    invalid = false
                },
                label = { Text(stringResource(R.string.settings_own_email_label)) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.settings_own_email_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                if (onAdd(text)) text = "" else invalid = true
            }) {
                Icon(Icons.Filled.Add, stringResource(R.string.settings_add_email))
            }
        }
    }
}

@Composable
private fun RemovableRow(text: String, removeDescription: String, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1f))
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, removeDescription) }
    }
}
