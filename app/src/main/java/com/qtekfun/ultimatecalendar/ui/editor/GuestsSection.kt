// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.ContactSuggestion
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.editor.withoutGuest
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/**
 * The organizer (read-only), the invited people as removable rows, and a field to add more by
 * email. Contact suggestions appear only once the optional permission is granted, which the
 * user asks for here and nowhere else.
 */
@Composable
internal fun GuestsSection(
    state: EditorUiState.Ready,
    actions: GuestActions,
    onChange: ((EventForm) -> EventForm) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    state.organizer?.let { organizer ->
        EditorRow(Icons.Filled.Person) {
            RowText(stringResource(R.string.editor_organizer, organizer), secondary = true)
        }
    }
    state.form.guests.forEachIndexed { index, guest ->
        GuestRow(guest, showIcon = index == 0 && state.organizer == null) {
            onChange { it.withoutGuest(guest) }
        }
    }
    GuestField(
        text = text,
        invalid = state.invalidGuest,
        showIcon = state.organizer == null && state.form.guests.isEmpty(),
        onTextChange = { typed ->
            text = typed
            if (state.invalidGuest != null) actions.onClearInvalid()
            actions.onType(typed)
            // A comma, semicolon or space ends an address, as in a pasted list.
            if (endsAnAddress(typed) && actions.onAdd(typed)) text = ""
        },
        onSubmit = { if (actions.onAdd(text)) text = "" }
    )
    state.contacts.forEach { contact ->
        SuggestionRow(contact) {
            if (actions.onAdd(contact.email)) text = ""
        }
    }
    ContactsPermission(state.contactsAvailable) { actions.onContactsAnswer(text) }
    Text(
        stringResource(R.string.editor_guests_hint),
        modifier = Modifier.padding(start = Spacing.xxl + Spacing.xl, end = Spacing.l),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val SEPARATORS = setOf(',', ';', ' ', '\n')

private fun endsAnAddress(typed: String) =
    typed.isNotEmpty() && typed.last() in SEPARATORS && typed.any { it !in SEPARATORS }

@Composable
private fun GuestRow(guest: Attendee, showIcon: Boolean, onRemove: () -> Unit) {
    val label = guest.name?.let { "$it (${guest.email})" } ?: guest.email
    EditorRow(if (showIcon) Icons.Filled.Person else null) {
        RowText(label)
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, stringResource(R.string.editor_guest_remove, guest.email))
        }
    }
}

@Composable
private fun GuestField(
    text: String,
    invalid: String?,
    showIcon: Boolean,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    EditorRow(if (showIcon) Icons.Filled.Person else null) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(stringResource(R.string.editor_guest_add)) },
            isError = invalid != null,
            supportingText = invalid?.let {
                { Text(stringResource(R.string.editor_guest_invalid, it)) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSubmit) {
            Icon(Icons.Filled.Add, stringResource(R.string.editor_guest_add_button))
        }
    }
}

@Composable
private fun SuggestionRow(contact: ContactSuggestion, onClick: () -> Unit) {
    val label = contact.name?.let { "$it · ${contact.email}" } ?: contact.email
    EditorRow(
        icon = null,
        onClick = onClick,
        description = stringResource(R.string.editor_guest_suggestion, label)
    ) { RowText(label) }
}

/** Asks for `READ_CONTACTS` on request: a button until it is granted, nothing after. */
@Composable
private fun ContactsPermission(granted: Boolean, onAnswer: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onAnswer() }
    if (!granted) {
        Column(Modifier.padding(start = Spacing.xxl + Spacing.xl, end = Spacing.l)) {
            TextButton(onClick = { launcher.launch(Manifest.permission.READ_CONTACTS) }) {
                Text(stringResource(R.string.editor_contacts_allow))
            }
            Text(
                stringResource(R.string.editor_contacts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
