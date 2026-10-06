// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import java.text.Normalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SqlLikeTest {
    /** What SQLite's LIKE does with [pattern] and ESCAPE '\': `%` any run, `_` one character. */
    private fun likes(pattern: String, text: String): Boolean {
        val regex = buildString {
            var index = 0
            while (index < pattern.length) {
                when (val c = pattern[index]) {
                    '\\' -> append(Regex.escape(pattern[++index].toString()))
                    '%' -> append(".*")
                    '_' -> append(".")
                    else -> append(Regex.escape(c.toString()))
                }
                index++
            }
        }
        return Regex(regex, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .matches(text)
    }

    @Test
    fun `wildcards and the escape character are escaped`() {
        assertEquals("100\\%\\_\\\\", SqlLike.escape("100%_\\"))
        assertEquals("plain", SqlLike.escape("plain"))
    }

    @Test
    fun `a word that can carry no accent stays literal`() {
        assertEquals("%bmpqvx%", SqlLike.contains("bmpqvx"))
        assertEquals("%100\\%%", SqlLike.contains("100%"))
        assertEquals("%\\_%", SqlLike.contains("_"))
        assertEquals("%\\\\%", SqlLike.contains("\\"))
    }

    @Test
    fun `letters that can carry an accent and non-ASCII characters become single wildcards`() {
        assertEquals("%_b_%", SqlLike.contains("ab" + "é"))
        assertEquals("%_______%", SqlLike.contains("reunion"))
        assertEquals("%b_____%", SqlLike.contains("budget"))
        assertEquals("%__%", SqlLike.contains("жж"))
    }

    @Test
    fun `the most selective word has the most characters that stay literal`() {
        // "reunion" is longer but every letter of it becomes a wildcard; "5b" keeps two.
        assertEquals("5b", SqlLike.mostSelective(listOf("reunion", "5b")))
        // With as many literal characters, the longer one.
        assertEquals("abc", SqlLike.mostSelective(listOf("ab", "abc")))
        assertEquals("only", SqlLike.mostSelective(listOf("only")))
    }

    @Test
    fun `a character outside the basic plane is one wildcard, as SQL counts characters`() {
        assertEquals("%_%", SqlLike.contains("😀"))
    }

    @Test
    fun `the accentable letters are exactly the ones with an accented form in Latin-1 to Latin Extended-B`() {
        val withVariants = ('a'..'z').filter { letter ->
            (0xC0..0x24F).any { code ->
                val folded = Normalizer.normalize(code.toChar().toString(), Normalizer.Form.NFD)
                    .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
                    .lowercase()
                folded == letter.toString() && code.toChar().toString() != letter.toString()
            }
        }

        assertEquals(withVariants.joinToString(""), SqlLike.ACCENTABLE)
    }

    @Test
    fun `the pattern never leaves out a text the matcher would find`() {
        val cases = listOf(
            "reunion" to "Reunión semanal",
            "reunion" to "REUNION",
            "cafe" to "Café con Zoë",
            "zoe" to "Zoë",
            "muñoz".let { SearchText.fold(it) } to "MUÑOZ",
            "100%" to "Done 100% today",
            "a_b" to "plan a_b",
            "ł" to "Łódź",
            "straße" to "Straße 5"
        )

        cases.forEach { (word, text) ->
            assertTrue(SearchText.fold(text).contains(SearchText.fold(word))) { "$word in $text" }
            assertTrue(likes(SqlLike.contains(SearchText.fold(word)), text)) { "$word in $text" }
        }
    }

    @Test
    fun `the pattern still leaves out what cannot match`() {
        assertFalse(likes(SqlLike.contains("100%"), "1000 done"))
        assertFalse(likes(SqlLike.contains("a_b"), "plan axb"))
        assertFalse(likes(SqlLike.contains("budget"), "Lunch"))
    }
}
