// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import java.text.Normalizer
import java.util.Locale

/**
 * Text folded for comparing: lowercase and without accents, so "reunion" finds "Reunión". Folding
 * can change the length of a text ("İ" becomes two characters), so [Folded] remembers where each
 * folded character came from, to highlight the original text.
 */
object SearchText {
    /** A folded text and, for each of its characters, the span of the original it came from. */
    class Folded internal constructor(
        val text: String,
        private val starts: IntArray,
        private val ends: IntArray
    ) {
        /** The span of the original text that the folded span [start] until [endExclusive] covers. */
        fun original(start: Int, endExclusive: Int): IntRange =
            starts[start] until ends[endExclusive - 1]
    }

    fun fold(text: String): String = foldWithMap(text).text

    fun foldWithMap(text: String): Folded {
        val folded = StringBuilder(text.length)
        val starts = ArrayList<Int>(text.length)
        val ends = ArrayList<Int>(text.length)
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val next = index + Character.charCount(codePoint)
            val piece = foldCodePoint(String(Character.toChars(codePoint)))
            // A separate accent leaves nothing of its own: it joins the letter before it.
            if (piece.isEmpty() && ends.isNotEmpty()) ends[ends.lastIndex] = next
            piece.forEach {
                folded.append(it)
                starts.add(index)
                ends.add(next)
            }
            index = next
        }
        return Folded(folded.toString(), starts.toIntArray(), ends.toIntArray())
    }

    private fun foldCodePoint(character: String): String =
        Normalizer.normalize(character, Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .lowercase(Locale.ROOT)
}
