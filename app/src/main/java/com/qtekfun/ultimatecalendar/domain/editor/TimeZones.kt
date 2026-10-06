// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** A time zone as the picker lists it: [city] ("New York"), its long [name] and its offset now. */
data class ZoneOption(val id: ZoneId, val city: String, val name: String, val offset: ZoneOffset) {
    /** The offset as people write it: `GMT+02:00`, `GMT` for zero. */
    val offsetLabel: String
        get() = if (offset.totalSeconds == 0) "GMT" else "GMT" + offset.id
}

/** The list of zones of the editor's picker, and the search over it. */
object TimeZones {
    private val ALWAYS_LISTED = setOf("UTC", "GMT")
    private val REGIONS = setOf(
        "Africa", "America", "Antarctica", "Arctic", "Asia", "Atlantic", "Australia", "Europe",
        "Indian", "Pacific"
    )

    /**
     * Every zone worth offering, ordered by offset at [now] and then by city. [first] zones (the
     * phone's and the event's) lead the list, once each. Legacy aliases such as `US/Eastern`
     * or `Etc/GMT+5` are left out.
     */
    fun options(now: Instant, locale: Locale, first: List<ZoneId> = emptyList()): List<ZoneOption> {
        val ids = ZoneId.getAvailableZoneIds().filter(::isListed).map { ZoneId.of(it) }
        val lead = first.distinct()
        val rest = (ids - lead.toSet()).map { option(it, now, locale) }
            .sortedWith(compareBy({ it.offset.totalSeconds }, { it.city }, { it.id.id }))
        return lead.map { option(it, now, locale) } + rest
    }

    /**
     * The [options] that match [query], ignoring case: every word of it must appear in the city
     * or region ("york", "europe"), the long name ("pacific"), the offset ("gmt+02") or the id.
     * A blank query matches everything.
     */
    fun search(options: List<ZoneOption>, query: String): List<ZoneOption> {
        val words = query.trim().lowercase(Locale.ROOT).split(' ').filter { it.isNotEmpty() }
        return options.filter { option ->
            val text = haystack(option)
            words.all { it in text }
        }
    }

    private fun haystack(option: ZoneOption): String = listOf(
        option.id.id.replace('_', ' '),
        option.city,
        option.name,
        option.offsetLabel,
        option.offset.id
    ).joinToString(" ").lowercase(Locale.ROOT)

    private fun option(zone: ZoneId, now: Instant, locale: Locale) = ZoneOption(
        id = zone,
        city = zone.id.substringAfterLast('/').replace('_', ' '),
        name = zone.getDisplayName(TextStyle.FULL, locale),
        offset = zone.rules.getOffset(now)
    )

    private fun isListed(id: String): Boolean =
        id in ALWAYS_LISTED || id.substringBefore('/') in REGIONS
}
