// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Every file of the corpus survives reading and writing, and an edit touches only its line. */
class IcsRoundTripTest {
    private val corpus: List<File> = IcsCorpus.files

    @TestFactory
    fun `files are written back byte for byte`() = corpus.flatMap { file ->
        val text = file.readText()
        listOf(text, text.replace("\r\n", "\n").replace("\n", "\r\n")).mapIndexed { i, variant ->
            DynamicTest.dynamicTest("${file.parentFile?.name}/${file.name} #$i") {
                val written = IcsWriter.write(IcsParser.parse(variant))
                // Components left open in the file are closed; nothing else may change.
                assertEquals(variant, written.take(variant.length))
                assertTrue(written.drop(variant.length).matches(Regex("(END:[A-Z]+\r\n)*")))
            }
        }
    }

    @TestFactory
    fun `changing the summary leaves every other line untouched`() = corpus.map { file ->
        DynamicTest.dynamicTest("${file.parentFile?.name}/${file.name}") {
            val text = file.readText()
            val calendar = IcsParser.parse(text).single()
            val event = calendar.components("VEVENT").firstOrNull() ?: return@dynamicTest
            val edited = calendar.withComponents(
                "VEVENT",
                calendar.components("VEVENT").map {
                    if (it === event) {
                        it.withProperty("SUMMARY", IcsProperty("SUMMARY", value = "Edited"))
                    } else {
                        it
                    }
                }
            )
            val before = lines(text).toMutableList()
            before[before.indexOfFirst { it.startsWith("SUMMARY") }] = "SUMMARY:Edited"
            val after = lines(IcsWriter.write(edited))
            assertEquals(before, after.take(before.size))
        }
    }

    /** Unfolded lines without their line breaks. */
    private fun lines(text: String) = text.replace("\r\n", "\n").replace("\n ", "")
        .replace("\n\t", "").split("\n").filter { it.isNotEmpty() }
}
