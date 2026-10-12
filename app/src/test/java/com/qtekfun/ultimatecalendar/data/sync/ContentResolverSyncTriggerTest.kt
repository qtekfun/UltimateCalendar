// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ContentResolverSyncTriggerTest {
    private val google = CalendarAccount("me@gmail.com", "com.google")
    private val calls = mutableListOf<Set<String>>()
    private var refused: Set<String>? = null

    private fun trigger(sdk: Int) = ContentResolverSyncTrigger(sdk) { account, flags ->
        assertEquals(google, account)
        calls += flags
        if (flags == refused) throw IllegalArgumentException("refused")
    }

    @Test
    fun `a background request carries no flag, so the system may batch and delay it`() {
        trigger(sdk = 36).request(google, expedited = false)
        assertEquals(listOf(emptySet<String>()), calls)
    }

    @Test
    fun `an urgent request runs as an expedited job from Android 12`() {
        trigger(sdk = 31).request(google, expedited = true)
        assertEquals(listOf(setOf("force", "schedule_as_expedited_job")), calls)
    }

    @Test
    fun `before Android 12 an urgent request only goes first in the queue`() {
        trigger(sdk = 30).request(google, expedited = true)
        assertEquals(listOf(setOf("force", "expedited")), calls)
    }

    @Test
    fun `a refused expedited job is asked again first in the queue`() {
        refused = setOf("force", "schedule_as_expedited_job")
        trigger(sdk = 36).request(google, expedited = true)
        assertEquals(
            listOf(setOf("force", "schedule_as_expedited_job"), setOf("force", "expedited")),
            calls
        )
    }

    @Test
    fun `a refusal of the old way too reaches the caller`() {
        refused = setOf("force", "expedited")
        assertThrows<IllegalArgumentException> { trigger(sdk = 30).request(google, true) }
    }
}
