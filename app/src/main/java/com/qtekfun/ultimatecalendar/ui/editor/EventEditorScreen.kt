// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.EditorRequest
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.ui.components.CalendarSnackbarHost
import com.qtekfun.ultimatecalendar.ui.components.EmptyState
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.ZoneId

/**
 * The create/edit event screen (RF-05) for [request]. [onClose] is called when the editor is
 * done: after saving, after discarding, or on back with nothing changed. Back, up and the system
 * gesture all go through the same "discard your changes?" check.
 */
@Composable
fun EventEditorRoute(
    request: EditorRequest,
    onClose: () -> Unit,
    viewModel: EventEditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(request) { viewModel.start(request) }
    if (state is EditorUiState.Closed) {
        LaunchedEffect(Unit) {
            viewModel.reset()
            onClose()
        }
    }
    BackHandler { viewModel.leave() }
    EventEditorScreen(
        state = state,
        device = remember { ZoneId.systemDefault() },
        actions = EditorActions(
            onEdit = viewModel::edit,
            onSave = viewModel::save,
            onLeave = viewModel::leave,
            onDismiss = viewModel::dismiss,
            guests = GuestActions(
                onAdd = viewModel::addGuests,
                onType = viewModel::suggestGuests,
                onContactsAnswer = viewModel::suggestGuests,
                onClearInvalid = { viewModel.dismiss(Dismissal.INVALID_GUEST) }
            )
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventEditorScreen(
    state: EditorUiState,
    device: ZoneId,
    actions: EditorActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() }
) {
    val ready = state as? EditorUiState.Ready
    val error = ready?.saveError?.let { saveErrorText(it) }
    LaunchedEffect(error) {
        if (error != null) {
            snackbar.showSnackbar(error)
            actions.onDismiss(Dismissal.SAVE_ERROR)
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { EditorTopBar(ready, actions) },
        snackbarHost = { CalendarSnackbarHost(snackbar) }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (state) {
                EditorUiState.Loading, EditorUiState.Closed -> LoadingIndicator()

                is EditorUiState.Failed -> EmptyState(
                    title = stringResource(R.string.editor_title_edit),
                    body = failureText(state.reason)
                )

                is EditorUiState.Ready -> EditorBody(state, device, actions)
            }
        }
    }
    if (ready != null) EditorPrompts(ready, actions)
}

@Composable
private fun LoadingIndicator() {
    val description = stringResource(R.string.editor_loading)
    CircularProgressIndicator(
        Modifier.padding(Spacing.xxl).semantics { contentDescription = description }
    )
}

@Composable
private fun EditorBody(state: EditorUiState.Ready, device: ZoneId, actions: EditorActions) {
    val form = state.form
    Column(
        Modifier
            .widthIn(max = Dimens.contentMaxWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        TitleField(form.title) { text -> actions.onEdit { it.copy(title = text) } }
        TimeSection(form, device, actions.onEdit)
        RepeatSection(form, actions.onEdit)
        Divider()
        GuestsSection(state, actions.guests, actions.onEdit)
        Divider()
        LocationAndDescription(form, actions.onEdit)
        CalendarSection(state, actions.onEdit)
        Divider()
        ReminderSection(form, state.allDayMinute, actions.onEdit)
        Divider()
        // A little room so the last row is not under the navigation bar or the keyboard.
        Box(Modifier.padding(bottom = Spacing.xxl))
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(vertical = Spacing.s))
}

@Composable
private fun TitleField(title: String, onChange: (String) -> Unit) {
    TextField(
        value = title,
        onValueChange = onChange,
        placeholder = {
            Text(
                stringResource(R.string.editor_title_hint),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(ready: EditorUiState.Ready?, actions: EditorActions) {
    val title = if (ready?.isNew == false) R.string.editor_title_edit else R.string.shell_new_event
    TopAppBar(
        title = {
            Text(stringResource(title), modifier = Modifier.semantics { heading() })
        },
        navigationIcon = {
            IconButton(onClick = { actions.onLeave(false) }) {
                Icon(Icons.Filled.Close, stringResource(R.string.editor_close))
            }
        },
        actions = {
            Button(
                onClick = { actions.onSave(null) },
                enabled = ready?.canSave == true,
                modifier = Modifier.padding(end = Spacing.s)
            ) { Text(stringResource(R.string.editor_save)) }
        }
    )
}
