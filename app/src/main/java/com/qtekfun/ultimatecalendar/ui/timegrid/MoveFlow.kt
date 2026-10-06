// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.timegrid.MoveRules
import com.qtekfun.ultimatecalendar.ui.components.LocalSnackbarHost
import com.qtekfun.ultimatecalendar.ui.components.showUndo
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/**
 * Connects the grid to saving a move (T18): what can be picked up, the question "which
 * occurrences?" for a repeating event, the new position shown while saving, and the messages
 * (Undo after a change; why a change failed or cannot be started). [content] draws the grid with
 * the [GridEditing] and the change being saved, or waiting for its occurrences to be chosen.
 */
@Composable
internal fun MoveFlow(
    viewModel: EventMoveViewModel,
    content: @Composable (GridEditing, PendingMove?) -> Unit
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val saving by viewModel.pending.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf<ScopeRequest?>(null) }
    val snackbar = LocalSnackbarHost.current ?: remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val readOnly = stringResource(R.string.drag_read_only)
    MoveMessages(viewModel, snackbar)
    // Once the change is being saved, its own position takes over from the question.
    LaunchedEffect(saving) { if (saving != null) asking = null }
    val editing = GridEditing(
        canPickUp = { MoveRules.canPickUp(rules, it) },
        onBlocked = {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(readOnly)
            }
        },
        onMove = { instance, time ->
            if (instance.isRecurring) {
                val choices = rules[instance.calendarId]?.scopes ?: RecurrenceScope.entries
                asking = ScopeRequest(PendingMove(instance, time), choices)
            } else {
                viewModel.move(instance, time, null)
            }
        }
    )
    content(editing, saving ?: asking?.move)
    asking?.takeIf { saving == null }?.let { request ->
        MoveScopeDialog(
            request,
            onConfirm = { viewModel.move(request.move.instance, request.move.newTime, it) },
            onDismiss = { asking = null }
        )
    }
}

/** Tells the user what happened to their change, once: Undo after a change, or why it failed. */
@Composable
private fun MoveMessages(viewModel: EventMoveViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            // Each message gets its own coroutine: one waiting for Undo must not hold the next.
            launch {
                snackbar.currentSnackbarData?.dismiss()
                when (message) {
                    is MoveMessage.Moved -> {
                        val undone = snackbar.showUndo(
                            movedText(context, message.move),
                            context.getString(R.string.cal_undo)
                        )
                        if (undone) viewModel.undo(message.undo)
                    }

                    is MoveMessage.Failed -> snackbar.showSnackbar(
                        failureText(context, message.error, R.string.drag_failed)
                    )

                    is MoveMessage.UndoFailed -> snackbar.showSnackbar(
                        failureText(context, message.error, R.string.drag_undo_failed)
                    )
                }
            }
        }
    }
}

private fun failureText(context: Context, error: CalendarError, @StringRes fallback: Int): String =
    context.getString(
        when (error) {
            is CalendarError.ReadOnly -> R.string.detail_error_read_only
            is CalendarError.PermissionDenied -> R.string.detail_error_permission
            else -> fallback
        }
    )

/** "Event moved to Mar 12, 2026, 9:15 AM", or "Event now ends at ..." when only the end moved. */
private fun movedText(context: Context, move: PendingMove): String {
    val locale = Locale.current.platformLocale
    val zone = ZoneId.systemDefault()
    val before = move.instance.time
    return when (val after = move.newTime) {
        is EventTime.AllDay -> context.getString(
            R.string.drag_moved,
            after.startDate.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            )
        )

        is EventTime.Timed -> {
            val format = DateTimeFormatter.ofLocalizedDateTime(
                FormatStyle.MEDIUM,
                FormatStyle.SHORT
            )
                .withLocale(locale)
            if ((before as? EventTime.Timed)?.start == after.start) {
                context.getString(R.string.drag_resized, after.end.atZone(zone).format(format))
            } else {
                context.getString(R.string.drag_moved, after.start.atZone(zone).format(format))
            }
        }
    }
}
