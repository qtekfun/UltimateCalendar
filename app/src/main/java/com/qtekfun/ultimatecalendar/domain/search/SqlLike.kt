// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

/**
 * `LIKE` patterns for a word, for a source that filters in SQL. The pattern is only a filter that
 * never leaves out a real match: SQL `LIKE` ignores case for plain letters only and knows nothing
 * of accents, so every letter that can carry an accent, and every non-ASCII character, becomes the
 * single-character wildcard `_`. [SearchMatcher] makes the final decision. The word always travels
 * as a bound argument, never inside the SQL text; use it with `ESCAPE` [ESCAPE].
 */
object SqlLike {
    const val ESCAPE = '\\'

    /** The ASCII letters that have an accented form in Latin-1 and Latin Extended-A and -B. */
    const val ACCENTABLE = "acdeghijklnorstuwyz"

    private const val MAX_ASCII = 127
    private const val WEIGHT = 100

    /** Makes [text] match itself literally: the escape character, `%` and `_` are escaped. */
    fun escape(text: String): String = buildString {
        text.forEach {
            if (it == ESCAPE || it == '%' || it == '_') append(ESCAPE)
            append(it)
        }
    }

    /** The word of [words] whose pattern keeps the most plain characters, so it filters best. */
    fun mostSelective(words: List<String>): String = words.maxBy { word ->
        word.count { it.code <= MAX_ASCII && it !in ACCENTABLE } * WEIGHT +
            word.length
    }

    /** The pattern that finds [word] (already folded) anywhere in a text. */
    fun contains(word: String): String = buildString {
        append('%')
        // One wildcard per code point: SQL counts characters, not UTF-16 units.
        word.codePoints().forEach { codePoint ->
            if (codePoint > MAX_ASCII || codePoint.toChar() in ACCENTABLE) {
                append('_')
            } else {
                append(escape(codePoint.toChar().toString()))
            }
        }
        append('%')
    }
}
