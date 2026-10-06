// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttendeeTest {
    @Test
    fun `only the unanswered status is pending`() {
        assertEquals(
            listOf(true, false, false, false),
            AttendeeStatus.entries.map { it.isPending }
        )
    }

    @Test
    fun `of normalizes the email`() {
        assertEquals("ana@example.com", Attendee.of("  Ana@Example.COM ").email)
    }

    @Test
    fun `an email that is not normalized is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Attendee("Ana@example.com") }
    }

    @Test
    fun `isOneOf ignores case and spaces`() {
        val ana = Attendee.of("ana@example.com")
        assertTrue(ana.isOneOf(listOf("bob@example.com", " ANA@example.com")))
        assertFalse(ana.isOneOf(listOf("bob@example.com")))
        assertFalse(ana.isOneOf(emptyList()))
    }
}
