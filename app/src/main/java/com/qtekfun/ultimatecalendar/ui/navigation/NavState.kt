// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** Which full-screen destination covers the shell, saved as plain values to survive rotation. */
class NavState {
    var search by mutableStateOf(false)
    var newEvent by mutableStateOf(false)
    var invitations by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var eventDetail by mutableStateOf(false)
    var help by mutableStateOf(false)

    companion object {
        val Saver: Saver<NavState, Any> = listSaver(
            save = {
                listOf(it.search, it.newEvent, it.invitations, it.settings, it.eventDetail, it.help)
            },
            restore = { values ->
                NavState().apply {
                    search = values[0]
                    newEvent = values[1]
                    invitations = values[2]
                    settings = values[3]
                    eventDetail = values.getOrElse(4) { false }
                    help = values.getOrElse(5) { false }
                }
            }
        )
    }
}
