// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import com.qtekfun.ultimatecalendar.R
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

        // T20: the event editor replaces this placeholder.
        nav.newEvent -> Placeholder(R.string.shell_new_event) { nav.newEvent = false }

        // T21: the invitations tray replaces this placeholder.
        nav.invitations -> Placeholder(R.string.shell_invitations) { nav.invitations = false }

        nav.settings -> {
            BackHandler { nav.settings = false }
            SettingsScreen(onBack = { nav.settings = false })
        }

        // T19: the event detail replaces this placeholder.
        nav.eventDetail -> Placeholder(R.string.timegrid_event_detail) { nav.eventDetail = false }

        // Help: a later task fills this in.
        nav.help -> Placeholder(R.string.shell_help) { nav.help = false }

        else -> ShellScreen(
            ShellActions(
                onSearch = { nav.search = true },
                onNewEvent = { nav.newEvent = true },
                onInvitations = { nav.invitations = true },
                onSettings = { nav.settings = true },
                onHelp = { nav.help = true },
                onOpenEvent = { nav.eventDetail = true },
                // T20: the editor will take the tapped time; for now it opens the same placeholder.
                onCreateAt = { nav.newEvent = true }
            )
        )
    }
}

@Composable
private fun Placeholder(@StringRes title: Int, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    PlaceholderScreen(title, onBack)
}
