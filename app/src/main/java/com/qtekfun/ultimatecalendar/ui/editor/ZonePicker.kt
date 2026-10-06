// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.TimeZones
import com.qtekfun.ultimatecalendar.domain.editor.ZoneOption
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId

private val ListMaxHeight = 320.dp

/**
 * The searchable list of time zones. The phone's and the event's zones come first; the offsets
 * shown are the ones in force at [at], the moment of the event.
 */
@Composable
internal fun ZonePicker(
    current: ZoneId,
    device: ZoneId,
    at: Instant,
    onPick: (ZoneId) -> Unit,
    onDismiss: () -> Unit
) {
    val locale = currentLocale()
    val all = remember(current, device, at, locale) {
        TimeZones.options(at, locale, listOf(device, current))
    }
    var query by rememberSaveable { mutableStateOf("") }
    val found = remember(all, query) { TimeZones.search(all, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_zone_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.editor_zone_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                if (found.isEmpty()) {
                    Text(
                        stringResource(R.string.editor_zone_none),
                        modifier = Modifier.padding(top = Spacing.l),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(Modifier.heightIn(max = ListMaxHeight)) {
                    items(found, key = { it.id.id }) { option ->
                        ZoneRow(option, option.id == device) {
                            onPick(option.id)
                            onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        }
    )
}

@Composable
private fun ZoneRow(option: ZoneOption, isDevice: Boolean, onClick: () -> Unit) {
    val title = if (isDevice) {
        stringResource(R.string.editor_zone_device, option.city)
    } else {
        option.city
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.s)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${option.offsetLabel} · ${option.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
