// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.editor.EditorRequest
import com.qtekfun.ultimatecalendar.domain.layout.AdaptiveLayout
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.notify.NotificationRoute
import com.qtekfun.ultimatecalendar.ui.account.CalDavAccountRoute
import com.qtekfun.ultimatecalendar.ui.adaptive.PaneDialog
import com.qtekfun.ultimatecalendar.ui.adaptive.currentAdaptiveLayout
import com.qtekfun.ultimatecalendar.ui.detail.EventDetailScreen
import com.qtekfun.ultimatecalendar.ui.editor.EventEditorRoute
import com.qtekfun.ultimatecalendar.ui.firstrun.LocalOpenWizard
import com.qtekfun.ultimatecalendar.ui.invitations.InvitationsScreen
import com.qtekfun.ultimatecalendar.ui.search.SearchScreen
import com.qtekfun.ultimatecalendar.ui.settings.SettingsScreen
import com.qtekfun.ultimatecalendar.ui.shell.DetailPane
import com.qtekfun.ultimatecalendar.ui.shell.ShellActions
import com.qtekfun.ultimatecalendar.ui.shell.ShellScreen
import com.qtekfun.ultimatecalendar.ui.shell.ShellViewModel

/**
 * The shell and what opens from it; back returns to the shell.
 *
 * On a wide window (see [AdaptiveLayout]) the event detail opens beside the Agenda instead of
 * covering the shell, and search and the invitations tray are dialogs over it. All of that is
 * decided from [NavState] and the window width, so rotating or resizing the window keeps what
 * was open: the same state is simply drawn the other way.
 */
@Composable
fun AppNavigation(routes: NotificationRoutes = remember { NotificationRoutes() }) {
    val nav = rememberSaveable(saver = NavState.Saver) { NavState() }
    val layout = currentAdaptiveLayout()
    val shell: ShellViewModel = viewModel()
    // The setup assistant of the first run (RF-01), reopened from the drawer.
    val openWizard = LocalOpenWizard.current
    // The shell's view lives in its ViewModel, so it survives rotation and resizing too.
    val shellState = shell.state.collectAsStateWithLifecycle()
    val view by remember { derivedStateOf { shellState.value.view } }
    OpenRequestedRoute(routes, nav, shell)
    val dialogs = layout.overlaysAsDialogs
    val detailInPane = layout.detailInPane(view, nav.overlayOpen())
    // T12: the first-run wizard (RF-01) becomes the first branch of this `when`.
    when {
        // Search stays under what opens from it (an event's detail, the editor): back returns here.
        nav.searchIsScreen(dialogs) -> SearchScreen(
            onBack = { nav.search = false },
            onOpenEvent = { nav.open(EventRef.of(it)) }
        )

        nav.newEvent -> EventEditorRoute(
            request = nav.editorOrNew(),
            onClose = nav::closeEditor
        )

        // The detail comes before the tray, so back from the detail returns to the tray.
        nav.eventDetail && !detailInPane -> EventDetailRoute(nav, inPane = false)

        !dialogs && nav.invitations -> {
            BackHandler { nav.invitations = false }
            InvitationsScreen(onBack = { nav.invitations = false }, onOpen = { nav.open(it) })
        }

        nav.account -> {
            BackHandler { nav.account = false }
            CalDavAccountRoute(onBack = { nav.account = false })
        }

        nav.settings -> {
            BackHandler { nav.settings = false }
            SettingsScreen(
                onBack = { nav.settings = false },
                onOpenAccount = { nav.account = true }
            )
        }

        else -> {
            ShellScreen(shellActions(nav, layout, openWizard), shell, nav.detailPane())
            WideOverlays(nav, dialogs)
        }
    }
}

/** The shell's way out to the rest of the app. */
private fun shellActions(nav: NavState, layout: AdaptiveLayout, openWizard: () -> Unit) =
    ShellActions(
        // Leaving the Agenda closes the detail beside it: the other views open it full screen.
        onSelectView = {
            if (layout.agendaTwoPane &&
                it != CalendarView.AGENDA
            ) {
                nav.eventDetail = false
            }
        },
        onSearch = { nav.search = true },
        onNewEvent = { nav.openEditor() },
        onInvitations = { nav.invitations = true },
        onSettings = { nav.settings = true },
        onSetup = openWizard,
        onOpenEvent = { nav.open(EventRef.of(it)) },
        onCreateAt = { nav.openEditor(EditorRequest.New(it)) }
    )

/** The Agenda's second pane: the same detail screen the phone opens full screen. */
private fun NavState.detailPane() = DetailPane(detailRef.takeIf { eventDetail }) {
    EventDetailRoute(this, inPane = true)
}

/** The detail of the event in [NavState.detailRef]; edit opens the editor on the same occurrence. */
@Composable
private fun EventDetailRoute(nav: NavState, inPane: Boolean) {
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
            },
            inPane = inPane
        )
    }
}

/** Search and the invitations tray in a dialog over the shell, on wide windows. */
@Composable
private fun WideOverlays(nav: NavState, enabled: Boolean) {
    if (!enabled) return
    if (nav.search) {
        PaneDialog(onDismiss = { nav.search = false }) {
            SearchScreen(
                onBack = { nav.search = false },
                onOpenEvent = { nav.open(EventRef.of(it)) }
            )
        }
    }
    if (nav.invitations) {
        PaneDialog(onDismiss = { nav.invitations = false }) {
            InvitationsScreen(onBack = { nav.invitations = false }, onOpen = { nav.open(it) })
        }
    }
}

/**
 * A tapped notification (RF-07) or widget (T38): the summary opens the tray, an invitation its
 * detail, a day of the Month widget the Day view, the "+" the editor.
 */
@Composable
private fun OpenRequestedRoute(routes: NotificationRoutes, nav: NavState, shell: ShellViewModel) {
    val requested by routes.pending.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        when (val route = requested) {
            NotificationRoute.Inbox -> nav.invitations = true

            is NotificationRoute.Event -> {
                nav.detailRef = route.ref
                nav.eventDetail = true
            }

            // A tapped widget (T38): back to the shell, on the Day view of that date.
            is NotificationRoute.Day -> {
                nav.closeAll()
                shell.selectView(CalendarView.DAY)
                shell.selectDate(route.date)
            }

            // The wizard's "Connect a CalDAV server": the login, with Settings behind it.
            NotificationRoute.ConnectCalDav -> {
                nav.closeAll()
                nav.settings = true
                nav.account = true
            }

            NotificationRoute.NewEvent -> {
                nav.closeAll()
                nav.openEditor(EditorRequest.New())
            }

            null -> return@LaunchedEffect
        }
        routes.consume()
    }
}
