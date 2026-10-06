// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import androidx.compose.runtime.saveable.SaverScope
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.editor.EditorRequest
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavStateEditorTest {
    private fun roundTrip(state: NavState): NavState {
        val saved = with(NavState.Saver) { SaverScope { true }.save(state) }
        return requireNotNull(NavState.Saver.restore(requireNotNull(saved)))
    }

    @Test
    fun `an open editor and what it edits survive a rotation`() {
        val ref = EventRef(EventId(7), 1_000, 2_000, allDay = false)
        val state = NavState().apply { openEditor(EditorRequest.Edit(ref)) }

        val restored = roundTrip(state)

        assertTrue(restored.newEvent)
        assertEquals(EditorRequest.Edit(ref), restored.editorRequest)
    }

    @Test
    fun `a tapped slot survives a rotation`() {
        val at = LocalDateTime.parse("2026-10-06T14:30")
        val restored = roundTrip(NavState().apply { openEditor(EditorRequest.New(at)) })

        assertEquals(EditorRequest.New(at), restored.editorRequest)
    }

    @Test
    fun `the editor keeps its own request apart from the event detail's`() {
        val detail = EventRef(EventId(3), 10, 20, allDay = true)
        val state = NavState().apply {
            detailRef = detail
            eventDetail = true
            openEditor()
        }

        val restored = roundTrip(state)

        assertEquals(detail, restored.detailRef)
        assertEquals(EditorRequest.New(), restored.editorRequest)
    }

    @Test
    fun `closing the editor forgets the request`() {
        val state = NavState().apply {
            openEditor(EditorRequest.New(LocalDateTime.parse("2026-10-06T14:30")))
            closeEditor()
        }

        assertFalse(state.newEvent)
        assertNull(state.editorRequest)
        assertNull(roundTrip(state).editorRequest)
    }
}
