// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.ui.theme.CalendarShapes
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/**
 * Shows [message] with an Undo action and returns true when the user pressed it. Call it from
 * a coroutine after doing the change, and revert when it returns true. The snackbar stays long
 * enough to read at large font sizes ([SnackbarDuration.Long]).
 */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): Boolean =
    showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long) ==
        SnackbarResult.ActionPerformed

/** The app's snackbar host: rounded, with a 48 dp action, above the navigation bar. */
@Composable
fun CalendarSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = CalendarShapes.snackbar,
            actionColor = MaterialTheme.colorScheme.inversePrimary
        )
    }
}

/**
 * A modal bottom sheet in the app's style: drag handle, 28 dp top corners, content kept clear of
 * the navigation bar. Dismiss by dragging, tapping outside or the back gesture (with the
 * system's predictive back animation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = CalendarShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.l)) { content() }
    }
}

/** Preview helper: the Undo snackbar as it looks, without a host. */
@ComponentPreviews
@Composable
internal fun UndoSnackbarPreview() {
    PreviewSurface {
        Snackbar(
            modifier = Modifier.padding(Spacing.l),
            shape = CalendarShapes.snackbar,
            action = { Text(stringResource(R.string.cal_undo)) }
        ) { Text("Event deleted") }
    }
}
