// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HighlightedTextTest {
    private fun field(text: String, vararg hits: IntRange) =
        FieldMatch(SearchField.DESCRIPTION, text, hits.toList())

    @Test
    fun `the text is cut at the highlights`() {
        val text = HighlightedText("Lunch with Ana today", listOf(0..4, 11..13))

        assertEquals(
            listOf(
                TextSegment("Lunch", true),
                TextSegment(" with ", false),
                TextSegment("Ana", true),
                TextSegment(" today", false)
            ),
            text.segments()
        )
    }

    @Test
    fun `a text without highlights is one plain segment, and an empty one has none`() {
        assertEquals(
            listOf(TextSegment("Plain", false)),
            HighlightedText("Plain", emptyList()).segments()
        )
        assertEquals(emptyList<TextSegment>(), HighlightedText("", emptyList()).segments())
    }

    @Test
    fun `a highlight at the very end leaves no empty piece`() {
        assertEquals(
            listOf(TextSegment("a ", false), TextSegment("bc", true)),
            HighlightedText("a bc", listOf(2..3)).segments()
        )
    }

    @Test
    fun `a short text is kept whole with its line breaks turned into spaces`() {
        val excerpt = HighlightedText.excerpt(field("one\ntwo\tthree", 4..6), maxLength = 40)

        assertEquals("one two three", excerpt.text)
        assertEquals(listOf(4..6), excerpt.highlights)
    }

    @Test
    fun `a long text is cut around the first highlight with ellipses`() {
        val text = "a".repeat(100) + "NEEDLE" + "b".repeat(100)

        val excerpt = HighlightedText.excerpt(field(text, 100..105), maxLength = 40)

        assertEquals("…" + "a".repeat(10) + "NEEDLE" + "b".repeat(24) + "…", excerpt.text)
        assertEquals(listOf(11..16), excerpt.highlights)
        assertEquals("NEEDLE", excerpt.text.substring(11, 17))
    }

    @Test
    fun `a highlight near the start needs no leading ellipsis`() {
        val text = "NEEDLE" + "b".repeat(100)

        val excerpt = HighlightedText.excerpt(field(text, 0..5), maxLength = 40)

        assertEquals("NEEDLE" + "b".repeat(34) + "…", excerpt.text)
        assertEquals(listOf(0..5), excerpt.highlights)
    }

    @Test
    fun `a highlight near the end needs no trailing ellipsis`() {
        val text = "b".repeat(100) + "NEEDLE"

        val excerpt = HighlightedText.excerpt(field(text, 100..105), maxLength = 40)

        assertEquals("…" + "b".repeat(10) + "NEEDLE", excerpt.text)
        assertEquals(listOf(11..16), excerpt.highlights)
    }

    @Test
    fun `highlights that fall outside the excerpt are dropped and the ones cut are trimmed`() {
        val text = "x".repeat(200)

        val excerpt = HighlightedText.excerpt(
            field(text, 0..1, 50..51, 62..63, 120..121),
            maxLength = 40
        )

        // The window is the first 40 characters: only the first highlight is in it.
        assertEquals(listOf(0..1), excerpt.highlights)
    }

    @Test
    fun `a highlight cut by the end of the window is trimmed to it`() {
        val text = "ab" + "x".repeat(37) + "WORD" + "y".repeat(50)

        val excerpt = HighlightedText.excerpt(field(text, 0..1, 39..42), maxLength = 40)

        assertEquals(listOf(0..1, 39..39), excerpt.highlights)
        assertEquals("ab" + "x".repeat(37) + "W…", excerpt.text)
    }
}
