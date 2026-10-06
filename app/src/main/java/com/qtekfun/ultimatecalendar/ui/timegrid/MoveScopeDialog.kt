// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.ui.detail.ScopeChoices
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/**
 * Asks which occurrences of a repeating event to move: only this one, this and the following, or
 * all (the same choices as deleting, T19). [request] lists the ones its calendar allows; a note
 * says why "this event" is missing when it is.
 */
@Composable
internal fun MoveScopeDialog(
    request: ScopeRequest,
    onConfirm: (RecurrenceScope) -> Unit,
    onDismiss: () -> Unit
) {
    var scope by rememberSaveable { mutableStateOf(request.scopes.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drag_scope_title)) },
        text = {
            Column {
                if (RecurrenceScope.THIS !in request.scopes) {
                    Text(
                        stringResource(R.string.drag_scope_local_note),
                        Modifier.padding(bottom = Spacing.s),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                ScopeChoices(scope, request.scopes) { scope = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(scope) },
                modifier = Modifier.heightIn(min = Dimens.minTouch)
            ) { Text(stringResource(R.string.drag_scope_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                Text(stringResource(R.string.detail_cancel))
            }
        }
    )
}
