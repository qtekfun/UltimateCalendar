// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.editor.EditorRequest

/** Which full-screen destination covers the shell, saved as plain values to survive rotation. */
class NavState {
    var search by mutableStateOf(false)
    var newEvent by mutableStateOf(false)
    var invitations by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var eventDetail by mutableStateOf(false)

    /** The occurrence the event detail shows; set together with [eventDetail]. */
    var detailRef by mutableStateOf<EventRef?>(null)

    /** The CalDAV account screen (RF-12): the login or the account, opened from Settings. */
    var account by mutableStateOf(false)

    /** What the event editor (open while [newEvent] is true) edits; null is a new event. */
    var editorRequest by mutableStateOf<EditorRequest?>(null)

    /** Opens the event editor: a new event, a new one at a tapped time, or an existing one. */
    fun openEditor(request: EditorRequest = EditorRequest.New()) {
        editorRequest = request
        newEvent = true
    }

    /** Back to the shell: what a widget tap starts from. */
    fun closeAll() {
        search = false
        invitations = false
        settings = false
        eventDetail = false
        account = false
        closeEditor()
    }

    fun closeEditor() {
        newEvent = false
        editorRequest = null
    }

    companion object {
        private const val DETAIL_INDEX = 6
        private const val EDITOR_INDEX = 7
        private const val ACCOUNT_INDEX = 8

        val Saver: Saver<NavState, Any> = listSaver(
            save = {
                listOf(
                    it.search,
                    it.newEvent,
                    it.invitations,
                    it.settings,
                    it.eventDetail,
                    // Slot 5 was the placeholder Help screen: kept so older saved states still line up.
                    false,
                    it.detailRef?.encode(),
                    it.editorRequest?.encode(),
                    it.account
                )
            },
            restore = { values ->
                NavState().apply {
                    search = values[0] as Boolean
                    newEvent = values[1] as Boolean
                    invitations = values[2] as Boolean
                    settings = values[3] as Boolean
                    eventDetail = values.getOrNull(4) as? Boolean ?: false
                    detailRef = EventRef.decode(values.getOrNull(DETAIL_INDEX) as? String)
                    account = values.getOrNull(ACCOUNT_INDEX) as? Boolean ?: false
                    editorRequest = EditorRequest.decode(
                        (values.getOrNull(EDITOR_INDEX) as? List<*>)?.filterIsInstance<String>()
                    )
                }
            }
        )
    }
}
