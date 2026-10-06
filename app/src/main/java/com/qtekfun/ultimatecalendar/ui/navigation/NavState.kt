// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.qtekfun.ultimatecalendar.domain.detail.EventRef

/** Which full-screen destination covers the shell, saved as plain values to survive rotation. */
class NavState {
    var search by mutableStateOf(false)
    var newEvent by mutableStateOf(false)
    var invitations by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var eventDetail by mutableStateOf(false)

    /** The occurrence the event detail shows; set together with [eventDetail]. */
    var detailRef by mutableStateOf<EventRef?>(null)
    var help by mutableStateOf(false)

    companion object {
        val Saver: Saver<NavState, Any> = listSaver(
            save = {
                listOf(
                    it.search,
                    it.newEvent,
                    it.invitations,
                    it.settings,
                    it.eventDetail,
                    it.help,
                    it.detailRef?.encode()
                )
            },
            restore = { values ->
                NavState().apply {
                    search = values[0] as Boolean
                    newEvent = values[1] as Boolean
                    invitations = values[2] as Boolean
                    settings = values[3] as Boolean
                    eventDetail = values.getOrNull(4) as? Boolean ?: false
                    help = values.getOrNull(5) as? Boolean ?: false
                    detailRef = EventRef.decode(values.lastOrNull() as? String)
                }
            }
        )
    }
}
