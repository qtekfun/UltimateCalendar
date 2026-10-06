// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import com.qtekfun.ultimatecalendar.domain.model.Attendee

/** Where in an event a word was found, from the most to the least telling. */
enum class SearchField(internal val weight: Int) {
    TITLE(TITLE_WEIGHT),
    LOCATION(LOCATION_WEIGHT),
    ATTENDEE(ATTENDEE_WEIGHT),
    DESCRIPTION(DESCRIPTION_WEIGHT)
}

private const val TITLE_WEIGHT = 8
private const val LOCATION_WEIGHT = 4
private const val ATTENDEE_WEIGHT = 3
private const val DESCRIPTION_WEIGHT = 1
private const val WORD_START_BONUS = 4

/** The words found in one [text] of an event; [highlights] are spans of [text], sorted and apart. */
data class FieldMatch(val field: SearchField, val text: String, val highlights: List<IntRange>) {
    val highlighted: HighlightedText get() = HighlightedText(text, highlights)
}

/**
 * An event that matches a query: where it matched ([fields], best first) and how well ([score],
 * higher is better).
 */
data class EventMatch(val event: SearchableEvent, val fields: List<FieldMatch>, val score: Int) {
    /** The title with the found words, or the plain title when they were found elsewhere. */
    val title: HighlightedText
        get() = fields.firstOrNull { it.field == SearchField.TITLE }?.highlighted
            ?: HighlightedText(event.title, emptyList())

    /** The best match outside the title: what the row shows under it to explain the result. */
    val detail: FieldMatch? get() = fields.firstOrNull { it.field != SearchField.TITLE }
}

/**
 * Decides whether an event matches a query: every word of the query must be found, folded (no
 * case, no accents), somewhere in the title, location, description or an attendee's name or
 * address. Pure, so a source and the screen agree on what a match is.
 */
object SearchMatcher {
    /** How the event matches [query], or null when some word is nowhere in it. */
    fun match(query: SearchQuery, event: SearchableEvent): EventMatch? {
        if (query.isBlank) return null
        val texts = listOf(SearchField.TITLE to event.title) +
            listOfNotNull(event.location?.let { SearchField.LOCATION to it }) +
            event.attendees.map { SearchField.ATTENDEE to label(it) } +
            listOfNotNull(event.description?.let { SearchField.DESCRIPTION to it })
        val found = texts.map { (field, text) -> Candidate(field, text, query.words) }
        val covered = query.words.all { word -> found.any { word in it.wordsFound } }
        return if (covered) {
            val fields = found.filter { it.highlights.isNotEmpty() }
                .map { FieldMatch(it.field, it.text, it.highlights) }
                .sortedByDescending { it.field.weight }
            EventMatch(event, fields, score(query, found))
        } else {
            null
        }
    }

    /** The spans of [text] where any of the folded [words] occurs, sorted and without overlap. */
    fun highlights(text: String, words: List<String>): List<IntRange> =
        Candidate(SearchField.TITLE, text, words).highlights

    /** How an attendee is written and searched: the name, then the address. */
    fun label(attendee: Attendee): String =
        listOfNotNull(attendee.name?.takeIf { it.isNotBlank() }, attendee.email).joinToString(" · ")

    /** Per word, the weight of the best field that has it, plus a bonus for starting a word. */
    private fun score(query: SearchQuery, found: List<Candidate>): Int = query.words.sumOf { word ->
        found.filter { word in it.wordsFound }.maxOf { it.field.weight + it.startBonus(word) }
    }

    /** One text, folded once, with the words it contains. */
    private class Candidate(val field: SearchField, val text: String, words: List<String>) {
        private val folded = SearchText.foldWithMap(text)
        private val spans: Map<String, List<IntRange>> = words.associateWith { occurrences(it) }
        val wordsFound: Set<String> = spans.filterValues { it.isNotEmpty() }.keys
        val highlights: List<IntRange> = merge(spans.values.flatten().map { folded.original(it) })

        /** Extra points when [word] starts a word of this text ("lun" in "Lunch", not "Blunt"). */
        fun startBonus(word: String): Int = if (spans.getValue(word).any {
                it.first == 0 ||
                    !folded.text[it.first - 1].isLetterOrDigit()
            }
        ) {
            WORD_START_BONUS
        } else {
            0
        }

        private fun occurrences(word: String): List<IntRange> = buildList {
            var from = if (word.isEmpty()) -1 else folded.text.indexOf(word)
            while (from >= 0) {
                add(from until from + word.length)
                from = folded.text.indexOf(word, from + 1)
            }
        }

        private fun SearchText.Folded.original(span: IntRange) = original(span.first, span.last + 1)

        private fun merge(spans: List<IntRange>): List<IntRange> {
            val merged = mutableListOf<IntRange>()
            spans.sortedBy { it.first }.forEach { span ->
                val last = merged.lastOrNull()
                if (last != null && span.first <= last.last + 1) {
                    merged[merged.lastIndex] = last.first..maxOf(last.last, span.last)
                } else {
                    merged.add(span)
                }
            }
            return merged
        }
    }
}
