// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

/** A piece of a text and whether it is one of the found words. */
data class TextSegment(val text: String, val highlighted: Boolean)

/** A [text] with the spans ([highlights], sorted, apart, inside the text) to draw emphasized. */
data class HighlightedText(val text: String, val highlights: List<IntRange>) {
    /** The text cut at the highlights, in order, without empty pieces. */
    fun segments(): List<TextSegment> = buildList {
        var position = 0
        highlights.forEach { span ->
            if (span.first > position) add(TextSegment(text.substring(position, span.first), false))
            add(TextSegment(text.substring(span.first, span.last + 1), true))
            position = span.last + 1
        }
        if (position < text.length) add(TextSegment(text.substring(position), false))
    }

    companion object {
        const val ELLIPSIS = "…"

        /**
         * A short excerpt of [source]'s text, at most about [maxLength] characters, that keeps the
         * first highlight in view: line breaks become spaces, and an ellipsis marks what was cut.
         * A text that fits is returned whole.
         */
        fun excerpt(source: FieldMatch, maxLength: Int): HighlightedText {
            val flat = source.text.map { if (it.isWhitespace()) ' ' else it }.joinToString("")
            val firstHit = source.highlights.firstOrNull()?.first ?: 0
            if (flat.length <= maxLength) return HighlightedText(flat, source.highlights)
            // Show some context before the first hit, but never start in the middle of nowhere.
            val start = (firstHit - maxLength / CONTEXT_DIVISOR).coerceAtLeast(0)
            val end = (start + maxLength).coerceAtMost(flat.length)
            val lead = if (start > 0) ELLIPSIS else ""
            val tail = if (end < flat.length) ELLIPSIS else ""
            val shift = lead.length - start
            val shifted = source.highlights
                .filter { it.last >= start && it.first < end }
                .map { (maxOf(it.first, start) + shift)..(minOf(it.last, end - 1) + shift) }
            return HighlightedText(lead + flat.substring(start, end) + tail, shifted)
        }

        private const val CONTEXT_DIVISOR = 4
    }
}
