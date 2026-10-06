// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

@StringRes
internal fun RecurrenceScope.label() = when (this) {
    RecurrenceScope.THIS -> R.string.detail_scope_this
    RecurrenceScope.THIS_AND_FOLLOWING -> R.string.detail_scope_following
    RecurrenceScope.ALL -> R.string.detail_scope_all
}

/**
 * Asks before deleting. A repeating event also asks which occurrences (this one, this and the
 * following, all); [hasAttendees] warns that the people invited will be told.
 */
@Composable
internal fun DeleteDialog(
    isSeries: Boolean,
    hasAttendees: Boolean,
    onConfirm: (RecurrenceScope) -> Unit,
    onDismiss: () -> Unit
) {
    var scope by rememberSaveable { mutableStateOf(RecurrenceScope.THIS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isSeries) {
                        R.string.detail_delete_series_title
                    } else {
                        R.string.detail_delete_title
                    }
                )
            )
        },
        text = {
            Column {
                if (!isSeries || hasAttendees) {
                    Text(
                        stringResource(
                            if (hasAttendees) {
                                R.string.detail_delete_attendees_text
                            } else {
                                R.string.detail_delete_text
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isSeries) ScopeChoices(scope) { scope = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(scope) },
                modifier = Modifier.heightIn(min = Dimens.minTouch)
            ) { Text(stringResource(R.string.detail_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                Text(stringResource(R.string.detail_cancel))
            }
        }
    )
}

/**
 * The ways to change part of a series, as one group of 48 dp radio rows: all three by default,
 * or the [options] a caller allows. Shared by the delete dialog and the move dialog (T18).
 */
@Composable
internal fun ScopeChoices(
    selected: RecurrenceScope,
    options: List<RecurrenceScope> = RecurrenceScope.entries,
    onSelect: (RecurrenceScope) -> Unit
) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouch)
                    .selectable(
                        selected = selected == option,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == option, onClick = null)
                Text(
                    stringResource(option.label()),
                    modifier = Modifier.padding(start = Spacing.m)
                )
            }
        }
    }
}
