// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/** The questions the editor may be waiting on: which occurrences to change, and discarding. */
@Composable
internal fun EditorPrompts(state: EditorUiState.Ready, actions: EditorActions) {
    when (state.prompt) {
        EditorPrompt.SCOPE -> ScopeDialog(
            scopes = state.scopes,
            onPick = actions.onSave,
            onDismiss = { actions.onDismiss(Dismissal.PROMPT) }
        )

        EditorPrompt.DISCARD -> DiscardDialog(
            onDiscard = { actions.onLeave(true) },
            onDismiss = { actions.onDismiss(Dismissal.PROMPT) }
        )

        null -> Unit
    }
}

/** "Only this event / This and following events / All events", the way Google asks it. */
@Composable
private fun ScopeDialog(
    scopes: List<RecurrenceScope>,
    onPick: (RecurrenceScope) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_scope_title)) },
        text = {
            Column {
                scopes.forEach { scope ->
                    Text(
                        stringResource(scope.label()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouch)
                            .clickable(role = Role.Button) { onPick(scope) }
                            .padding(vertical = Spacing.m)
                    )
                }
                if (RecurrenceScope.THIS !in scopes) {
                    Text(
                        stringResource(R.string.editor_scope_local_note),
                        modifier = Modifier.padding(top = Spacing.s),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        }
    )
}

private fun RecurrenceScope.label(): Int = when (this) {
    RecurrenceScope.THIS -> R.string.editor_scope_this
    RecurrenceScope.THIS_AND_FOLLOWING -> R.string.editor_scope_following
    RecurrenceScope.ALL -> R.string.editor_scope_all
}

@Composable
private fun DiscardDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_discard_title)) },
        text = { Text(stringResource(R.string.editor_discard_text)) },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.editor_discard)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_keep_editing)) }
        }
    )
}
