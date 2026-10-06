// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.editor.EditorRequest
import com.qtekfun.ultimatecalendar.ui.detail.EventDetailScreen
import com.qtekfun.ultimatecalendar.ui.editor.EventEditorRoute
import com.qtekfun.ultimatecalendar.ui.settings.SettingsScreen
import com.qtekfun.ultimatecalendar.ui.shell.ShellActions
import com.qtekfun.ultimatecalendar.ui.shell.ShellScreen

/**
 * The shell and what opens from it; back returns to the shell. Every destination except the
 * shell is a placeholder for a later task.
 */
@Composable
fun AppNavigation() {
    val nav = rememberSaveable(saver = NavState.Saver) { NavState() }
    // T12: the first-run wizard (RF-01) becomes the first branch of this `when`.
    when {
        // T22: Search replaces this placeholder.
        nav.search -> Placeholder(R.string.shell_search) { nav.search = false }

        nav.newEvent -> EventEditorRoute(
            request = nav.editorRequest ?: EditorRequest.New(),
            onClose = nav::closeEditor
        )

        // T21: the invitations tray replaces this placeholder.
        nav.invitations -> Placeholder(R.string.shell_invitations) { nav.invitations = false }

        nav.settings -> {
            BackHandler { nav.settings = false }
            SettingsScreen(onBack = { nav.settings = false })
        }

        nav.eventDetail -> {
            val ref = nav.detailRef
            if (ref == null) {
                nav.eventDetail = false
            } else {
                EventDetailScreen(
                    ref = ref,
                    onBack = { nav.eventDetail = false },
                    onEdit = {
                        nav.eventDetail = false
                        nav.openEditor(EditorRequest.Edit(ref))
                    }
                )
            }
        }

        // Help: a later task fills this in.
        nav.help -> Placeholder(R.string.shell_help) { nav.help = false }

        else -> ShellScreen(
            ShellActions(
                onSearch = { nav.search = true },
                onNewEvent = { nav.openEditor() },
                onInvitations = { nav.invitations = true },
                onSettings = { nav.settings = true },
                onHelp = { nav.help = true },
                onOpenEvent = {
                    nav.detailRef = EventRef.of(it)
                    nav.eventDetail = true
                },
                onCreateAt = { nav.openEditor(EditorRequest.New(it)) }
            )
        )
    }
}

@Composable
private fun Placeholder(@StringRes title: Int, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    PlaceholderScreen(title, onBack)
}
