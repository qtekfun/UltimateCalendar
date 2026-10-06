// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import androidx.compose.runtime.saveable.SaverScope
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.model.EventId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavStateTest {
    private fun roundTrip(state: NavState): NavState {
        val saved = with(NavState.Saver) { SaverScope { true }.save(state) }
        return requireNotNull(NavState.Saver.restore(requireNotNull(saved)))
    }

    @Test
    fun `the open event detail survives a rotation`() {
        val state = NavState().apply {
            settings = true
            eventDetail = true
            detailRef = EventRef(EventId(7), 1_000, 2_000, allDay = false)
        }

        val restored = roundTrip(state)

        assertTrue(restored.settings)
        assertTrue(restored.eventDetail)
        assertEquals(state.detailRef, restored.detailRef)
    }

    @Test
    fun `no event detail, no ref`() {
        val restored = roundTrip(NavState().apply { help = true })
        assertTrue(restored.help)
        assertNull(restored.detailRef)
    }

    @Test
    fun `a state saved before the ref existed still opens`() {
        val old = listOf(false, true, false, false, false, false)
        val restored = requireNotNull(NavState.Saver.restore(old))
        assertTrue(restored.newEvent)
        assertNull(restored.detailRef)
    }
}
