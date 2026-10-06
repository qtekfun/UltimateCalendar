// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.EventDetail
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.ui.components.CalendarSnackbarHost
import com.qtekfun.ultimatecalendar.ui.components.showUndo
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The event detail (RF-04) of the occurrence [ref]: what the event says, the answer buttons for
 * an invitation, and edit, delete and share in the top bar. [onEdit] opens the editor (T20).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    ref: EventRef,
    onBack: () -> Unit,
    onEdit: (EventRef) -> Unit,
    viewModel: EventDetailViewModel = viewModel()
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(ref) { viewModel.open(ref) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cannotOpen = stringResource(R.string.detail_cannot_open)
    val open = { uri: String ->
        if (!startView(context, uri)) scope.launch { snackbar.showSnackbar(cannotOpen) }
        Unit
    }
    val loaded = state as? DetailState.Loaded
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    FailureMessages(viewModel, snackbar)
    DeletedEffect(state as? DetailState.Deleted, snackbar, onBack, viewModel::undoDelete)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (loaded != null) {
                        DetailMenu(
                            loaded.detail,
                            enabled = !loaded.busy,
                            onEdit = { onEdit(ref) },
                            onDelete = { confirmDelete = true }
                        )
                    }
                }
            )
        },
        snackbarHost = { CalendarSnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (val current = state) {
                DetailState.Loading -> Progress()
                is DetailState.Failed -> FailedMessage(current.error, viewModel::retry)
                is DetailState.Loaded -> Loaded(current, open, viewModel)
                is DetailState.Deleted -> Unit
            }
        }
    }
    if (confirmDelete && loaded != null) {
        DeleteDialog(
            isSeries = loaded.detail.isSeries,
            hasAttendees = loaded.detail.attendees != null,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(it)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun Loaded(
    state: DetailState.Loaded,
    open: (String) -> Unit,
    viewModel: EventDetailViewModel
) {
    val configuration = LocalConfiguration.current
    val resources = LocalResources.current
    val words = remember(configuration, resources) {
        DetailWords(ResourceWords(resources), configuration.locales[0])
    }
    Column(Modifier.widthIn(max = Dimens.contentMaxWidth).fillMaxWidth()) {
        if (state.deleting) LinearProgressIndicator(Modifier.fillMaxWidth())
        DetailBody(
            state.detail,
            words,
            state.responding,
            DetailActions(onRespond = viewModel::respond, onOpenUri = open),
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl)
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.shell_back)
        )
    }
}

/** Edit and delete when the calendar allows changes, and share. */
@Composable
private fun DetailMenu(
    detail: EventDetail,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = LocalResources.current
    val chooser = stringResource(R.string.detail_share_chooser)
    if (detail.canEdit) {
        IconButton(onClick = onEdit, enabled = enabled) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.detail_edit))
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.detail_delete))
        }
    }
    IconButton(
        onClick = {
            val words = DetailWords(ResourceWords(resources), configuration.locales[0])
            share(context, chooser, detail, words)
        }
    ) {
        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.detail_share))
    }
}

@Composable
private fun Progress() {
    val label = stringResource(R.string.detail_loading)
    Box(
        Modifier.fillMaxSize().semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator() }
}

@Composable
private fun FailedMessage(error: CalendarError, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(
                if (error is CalendarError.NotFound) {
                    R.string.detail_not_found
                } else {
                    R.string.detail_load_failed
                }
            )
        )
        if (error !is CalendarError.NotFound) {
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                Text(stringResource(R.string.detail_retry))
            }
        }
    }
}

/** Tells the user why an answer or a deletion failed, in a snackbar, once. */
@Composable
private fun FailureMessages(viewModel: EventDetailViewModel, snackbar: SnackbarHostState) {
    val respond = stringResource(R.string.detail_respond_failed)
    val delete = stringResource(R.string.detail_delete_failed)
    val undo = stringResource(R.string.detail_undo_failed)
    val readOnly = stringResource(R.string.detail_error_read_only)
    val permission = stringResource(R.string.detail_error_permission)
    LaunchedEffect(viewModel) {
        viewModel.failure.collect { failure ->
            val message = when {
                failure.error is CalendarError.ReadOnly -> readOnly
                failure.error is CalendarError.PermissionDenied -> permission
                failure.action == DetailAction.RESPOND -> respond
                failure.action == DetailAction.DELETE -> delete
                else -> undo
            }
            snackbar.showSnackbar(message)
        }
    }
}

/**
 * After a deletion: offer Undo when the event can be put back, then leave. Without Undo the
 * screen closes at once.
 */
@Composable
private fun DeletedEffect(
    deleted: DetailState.Deleted?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onUndo: () -> Unit
) {
    val message = stringResource(R.string.detail_deleted)
    val undoLabel = stringResource(R.string.cal_undo)
    LaunchedEffect(deleted) {
        when {
            deleted == null -> Unit
            !deleted.canUndo -> onBack()
            snackbar.showUndo(message, undoLabel) -> onUndo()
            else -> onBack()
        }
    }
}

/** Opens [uri] (a web link or a `geo:`) in whichever app takes it; false when none does. */
private fun startView(context: Context, uri: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
    true
} catch (_: ActivityNotFoundException) {
    false
}

private fun share(context: Context, chooser: String, detail: EventDetail, words: DetailWords) {
    val lines = listOf(detail.title) + words.whenLines(detail.time) +
        listOfNotNull(detail.location, detail.joinUrl)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, detail.title)
        .putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
    try {
        context.startActivity(Intent.createChooser(send, chooser))
    } catch (_: ActivityNotFoundException) {
        // Nothing can take text: there is nothing to tell that the user could act on.
    }
}
