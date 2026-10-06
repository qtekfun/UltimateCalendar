// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import java.io.File

/** The files of src/test/resources/ics-corpus. */
object IcsCorpus {
    private val root =
        File(requireNotNull(IcsCorpus::class.java.getResource("/ics-corpus")).toURI())

    /** Every `.ics` file, sorted by path. */
    val files: List<File> =
        root.walk().filter { it.isFile && it.extension == "ics" }.sortedBy { it.path }.toList()

    /** The text of the corpus file [path] (`google/all-day-multiday.ics`), read as bytes of UTF-8. */
    fun text(path: String): String = File(root, path).readText(Charsets.UTF_8)

    /** The `VCALENDAR` of the corpus file [path]. */
    fun calendar(path: String): IcsComponent = IcsParser.parse(text(path)).single()
}
