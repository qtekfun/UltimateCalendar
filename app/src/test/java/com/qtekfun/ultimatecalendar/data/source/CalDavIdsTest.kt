// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalDavIdsTest {
    @Test
    fun `a row id survives the trip to the app id and back`() {
        listOf(1L, 2L, 12_345L, Int.MAX_VALUE.toLong(), (1L shl 62) - 1).forEach { row ->
            val id = CalDavIds.encode(row)
            assertTrue(CalDavIds.isCalDav(id))
            assertEquals(row, CalDavIds.decode(id))
        }
    }

    @Test
    fun `the provider's ids, however large, are never taken for CalDAV ones`() {
        listOf(1L, 7L, 1_000_000L, Int.MAX_VALUE.toLong(), 1L shl 40, (1L shl 62) - 1)
            .forEach { assertFalse(CalDavIds.isCalDav(it), "$it") }
    }

    @Test
    fun `negative values belong to neither source`() {
        // The test reminder uses Long.MIN_VALUE as its id.
        listOf(Long.MIN_VALUE, -1L, 0L).forEach { assertFalse(CalDavIds.isCalDav(it), "$it") }
    }

    @Test
    fun `a CalDAV id never equals a provider id`() {
        val provider = (1L..1_000L).toSet()
        assertTrue((1L..1_000L).map(CalDavIds::encode).none { it in provider })
    }

    @Test
    fun `the typed helpers agree with the numbers`() {
        assertEquals(CalendarId(CalDavIds.encode(3)), CalDavIds.calendar(3))
        assertEquals(EventId(CalDavIds.encode(9)), CalDavIds.event(9))
        assertTrue(CalDavIds.isCalDav(CalDavIds.calendar(3)))
        assertTrue(CalDavIds.isCalDav(CalDavIds.event(9)))
        assertFalse(CalDavIds.isCalDav(CalendarId(3)))
        assertFalse(CalDavIds.isCalDav(EventId(9)))
        assertEquals(3L, CalDavIds.rowOf(CalDavIds.calendar(3)))
        assertEquals(9L, CalDavIds.rowOf(CalDavIds.event(9)))
    }

    @Test
    fun `ids that are not rows or not CalDAV ones are refused`() {
        assertThrows(IllegalArgumentException::class.java) { CalDavIds.encode(0) }
        assertThrows(IllegalArgumentException::class.java) { CalDavIds.encode(1L shl 62) }
        assertThrows(IllegalArgumentException::class.java) { CalDavIds.decode(5) }
        assertThrows(IllegalArgumentException::class.java) { CalDavIds.decode(-5) }
    }
}
