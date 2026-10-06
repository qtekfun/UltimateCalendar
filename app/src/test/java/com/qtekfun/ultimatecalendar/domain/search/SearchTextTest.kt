// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchTextTest {
    @Test
    fun `folding drops case and accents`() {
        assertEquals("reunion", SearchText.fold("Reunión"))
        assertEquals("zoe munoz", SearchText.fold("ZOË Muñoz"))
        assertEquals("creme brulee", SearchText.fold("Crème Brûlée"))
    }

    @Test
    fun `text that is already folded does not change`() {
        assertEquals("plain text 123 @.", SearchText.fold("plain text 123 @."))
    }

    @Test
    fun `an accent written as a separate mark is dropped too`() {
        assertEquals("reunion", SearchText.fold("Reunión"))
    }

    @Test
    fun `letters without a decomposition are only lowercased`() {
        assertEquals("straße ø", SearchText.fold("STRAßE Ø"))
        assertEquals("привет", SearchText.fold("ПРИВЕТ"))
    }

    @Test
    fun `a character that folds to two keeps pointing at its single original`() {
        val folded = SearchText.foldWithMap("İx")

        // "İ" (dotted capital I) lowercases to "i" plus a combining dot, which is dropped.
        assertEquals("ix", folded.text)
        assertEquals(0 until 1, folded.original(0, 1))
        assertEquals(1 until 2, folded.original(1, 2))
    }

    @Test
    fun `a span of folded text maps back to the span of the original`() {
        val folded = SearchText.foldWithMap("Café Reunión")

        assertEquals("cafe reunion", folded.text)
        assertEquals(5 until 12, folded.original(5, 12))
        assertEquals(0 until 4, folded.original(0, 4))
    }

    @Test
    fun `a decomposed accent is part of the span of its letter`() {
        val folded = SearchText.foldWithMap("Reunión!")

        assertEquals("reunion!", folded.text)
        // The "o" and its separate accent are two original characters for one folded one.
        assertEquals(5 until 7, folded.original(5, 6))
        assertEquals(0 until 8, folded.original(0, 7))
    }

    @Test
    fun `characters outside the basic plane are kept whole`() {
        val folded = SearchText.foldWithMap("a😀b")

        assertEquals("a😀b", folded.text)
        assertEquals(1 until 3, folded.original(1, 3))
    }
}
