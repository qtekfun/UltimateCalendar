// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.editor.EditorRequest
import com.qtekfun.ultimatecalendar.notify.NotificationRoute
import com.qtekfun.ultimatecalendar.ui.detail.EventDetailScreen
import com.qtekfun.ultimatecalendar.ui.editor.EventEditorRoute
import com.qtekfun.ultimatecalendar.ui.invitations.InvitationsScreen
import com.qtekfun.ultimatecalendar.ui.search.SearchScreen
import com.qtekfun.ultimatecalendar.ui.settings.SettingsScreen
import com.qtekfun.ultimatecalendar.ui.shell.ShellActions
import com.qtekfun.ultimatecalendar.ui.shell.ShellScreen

/**
 * The shell and what opens from it; back returns to the shell. Every destination except the
 * shell is a placeholder for a later task.
 */
@Composable
fun AppNavigation(routes: NotificationRoutes = remember { NotificationRoutes() }) {
    val nav = rememberSaveable(saver = NavState.Saver) { NavState() }
    OpenRequestedRoute(routes, nav)
    // T12: the first-run wizard (RF-01) becomes the first branch of this `when`.
    when {
        // Search stays under what opens from it (an event's detail, the editor): back returns here.
        nav.search && !nav.eventDetail && !nav.newEvent -> SearchScreen(
            onBack = { nav.search = false },
            onOpenEvent = {
                nav.detailRef = EventRef.of(it)
                nav.eventDetail = true
            }
        )

        nav.newEvent -> EventEditorRoute(
            request = nav.editorRequest ?: EditorRequest.New(),
            onClose = nav::closeEditor
        )

        // The detail comes before the tray, so back from the detail returns to the tray.
        nav.eventDetail -> EventDetailRoute(nav)

        nav.invitations -> {
            BackHandler { nav.invitations = false }
            InvitationsScreen(
                onBack = { nav.invitations = false },
                onOpen = {
                    nav.detailRef = it
                    nav.eventDetail = true
                }
            )
        }

        nav.settings -> {
            BackHandler { nav.settings = false }
            SettingsScreen(onBack = { nav.settings = false })
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

/** The detail of the event in [NavState.detailRef]; edit opens the editor on the same occurrence. */
@Composable
private fun EventDetailRoute(nav: NavState) {
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

/** A tapped notification (RF-07): the summary opens the tray, an invitation opens its detail. */
@Composable
private fun OpenRequestedRoute(routes: NotificationRoutes, nav: NavState) {
    val requested by routes.pending.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        when (val route = requested) {
            NotificationRoute.Inbox -> nav.invitations = true

            is NotificationRoute.Event -> {
                nav.detailRef = route.ref
                nav.eventDetail = true
            }

            null -> return@LaunchedEffect
        }
        routes.consume()
    }
}

@Composable
private fun Placeholder(@StringRes title: Int, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    PlaceholderScreen(title, onBack)
}
