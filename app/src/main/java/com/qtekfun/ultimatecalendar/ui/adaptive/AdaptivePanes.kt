// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qtekfun.ultimatecalendar.ui.theme.CalendarShapes
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

private const val DIALOG_HEIGHT_FRACTION = 0.9f

/**
 * Centers [content] and keeps it no wider than the reading width (600 dp), so settings, the
 * event detail, the editor and the first-run wizard are not stretched across a tablet. On a
 * phone the window is narrower than the limit and nothing changes.
 */
@Composable
fun ReadingPane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), Alignment.TopCenter) {
        Box(Modifier.widthIn(max = Dimens.readingMaxWidth).fillMaxHeight()) { content() }
    }
}

/**
 * Search and the invitations tray on wide windows: the same screen, in a dialog over the shell
 * instead of covering it. Back and a tap outside call [onDismiss].
 */
@Composable
fun PaneDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(Spacing.xl)
                .widthIn(max = Dimens.dialogMaxWidth)
                .fillMaxWidth()
                .fillMaxHeight(DIALOG_HEIGHT_FRACTION),
            shape = CalendarShapes.card,
            tonalElevation = Spacing.s
        ) { content() }
    }
}
