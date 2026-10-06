// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Maps the TZID of a file to a java.time zone. Real files use IANA ids (`Europe/Madrid`), IANA
 * ids behind a path (`/mozilla.org/20050126_1/Europe/Madrid`, Thunderbird), Windows names
 * (`W. Europe Standard Time`, Outlook) and private names that only the file's own `VTIMEZONE`
 * explains. The last resort is the fixed offset that `VTIMEZONE` declares.
 */
object IcsZones {
    /** The zone of [tzid], or null when neither the name nor [calendar]'s VTIMEZONEs explain it. */
    // Direct name first, then the file's own definition: early returns keep it readable.
    @Suppress("ReturnCount")
    fun resolve(tzid: String, calendar: IcsComponent? = null): ZoneId? {
        val name = tzid.trim().trim('"')
        val direct = named(name) ?: windows(name)
        if (direct != null || calendar == null) return direct
        val definition = calendar.components("VTIMEZONE").firstOrNull {
            it.property("TZID")?.value?.trim()?.trim('"') == name
        } ?: return null
        return definition.property("X-LIC-LOCATION")?.value?.let { named(it.trim()) }
            ?: definition.property("X-WR-TIMEZONE")?.value?.let { named(it.trim()) }
            ?: fixedOffset(definition)
    }

    /** An IANA id, alone or after a path prefix such as `/mozilla.org/20050126_1/`. */
    private fun named(name: String): ZoneId? {
        val parts = name.split('/').filter { it.isNotEmpty() }
        val candidates = if (parts.size <= 1) {
            listOf(name)
        } else {
            (parts.size downTo 2).map { parts.takeLast(it).joinToString("/") }
        }
        return candidates.firstNotNullOfOrNull { candidate ->
            runCatching { ZoneId.of(candidate) }.getOrNull()
        }
    }

    private fun windows(name: String): ZoneId? =
        WINDOWS[name.lowercase(Locale.ROOT)]?.let { ZoneId.of(it) }

    /** The standard-time offset the definition declares, for zones nothing else identifies. */
    private fun fixedOffset(definition: IcsComponent): ZoneId? {
        val block = definition.components("STANDARD").lastOrNull()
            ?: definition.components("DAYLIGHT").lastOrNull()
        val text = block?.property("TZOFFSETTO")?.value?.trim() ?: return null
        return parseOffset(text)
    }

    /** `+0100`, `-0530` or `+010000`. */
    // Each malformed shape is refused on its own line.
    @Suppress("ReturnCount")
    internal fun parseOffset(text: String): ZoneOffset? {
        val sign = when (text.firstOrNull()) {
            '+' -> 1
            '-' -> -1
            else -> return null
        }
        val digits = text.drop(1)
        if (digits.length !in listOf(OFFSET_MINUTES, OFFSET_SECONDS) ||
            !digits.all(Char::isDigit)
        ) {
            return null
        }
        val hours = digits.substring(0, HH_END).toInt()
        val minutes = digits.substring(HH_END, MM_END).toInt()
        val seconds = digits.drop(MM_END).ifEmpty { "0" }.toInt()
        return runCatching {
            ZoneOffset.ofTotalSeconds(
                sign * (hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds)
            )
        }.getOrNull()
    }

    /** The Windows zone names the table knows, in lower case. */
    internal val windowsNames: Set<String> get() = WINDOWS.keys

    /** Whether [zone] is written as UTC (`...Z`) rather than with a TZID. */
    internal fun isUtc(zone: ZoneId): Boolean =
        zone.id == "UTC" || zone.id == "Z" || zone.id == "GMT" || zone is ZoneOffset

    /** Resolves the zones of one file, with the zone floating times are read in. */
    class Resolver(private val calendar: IcsComponent?, val floating: ZoneId) {
        /** The zone [date] is in: UTC, its TZID, or [floating] for floating and unknown ones. */
        fun zoneOf(date: IcsDate): ZoneId = when (val tzid = date.zone) {
            null -> floating
            IcsDate.UTC -> ZoneOffset.UTC
            else -> resolve(tzid, calendar) ?: floating
        }

        fun instant(date: IcsDate): Instant = date.toInstant(zoneOf(date))
    }

    private const val OFFSET_MINUTES = 4
    private const val OFFSET_SECONDS = 6
    private const val SECONDS_PER_HOUR = 3600
    private const val SECONDS_PER_MINUTE = 60
    private const val HH_END = 2
    private const val MM_END = 4

    /** Windows zone names (CLDR windowsZones, territory 001) to IANA ids; keys in lower case. */
    private val WINDOWS: Map<String, String> = mapOf(
        "dateline standard time" to "Etc/GMT+12",
        "utc-11" to "Etc/GMT+11",
        "hawaiian standard time" to "Pacific/Honolulu",
        "alaskan standard time" to "America/Anchorage",
        "pacific standard time" to "America/Los_Angeles",
        "pacific standard time (mexico)" to "America/Tijuana",
        "us mountain standard time" to "America/Phoenix",
        "mountain standard time" to "America/Denver",
        "mountain standard time (mexico)" to "America/Mazatlan",
        "central america standard time" to "America/Guatemala",
        "central standard time" to "America/Chicago",
        "central standard time (mexico)" to "America/Mexico_City",
        "canada central standard time" to "America/Regina",
        "sa pacific standard time" to "America/Bogota",
        "eastern standard time" to "America/New_York",
        "us eastern standard time" to "America/Indianapolis",
        "venezuela standard time" to "America/Caracas",
        "atlantic standard time" to "America/Halifax",
        "sa western standard time" to "America/La_Paz",
        "central brazilian standard time" to "America/Cuiaba",
        "pacific sa standard time" to "America/Santiago",
        "newfoundland standard time" to "America/St_Johns",
        "e. south america standard time" to "America/Sao_Paulo",
        "argentina standard time" to "America/Buenos_Aires",
        "sa eastern standard time" to "America/Cayenne",
        "greenland standard time" to "America/Godthab",
        "montevideo standard time" to "America/Montevideo",
        "utc-02" to "Etc/GMT+2",
        "mid-atlantic standard time" to "Etc/GMT+2",
        "azores standard time" to "Atlantic/Azores",
        "cape verde standard time" to "Atlantic/Cape_Verde",
        "utc" to "UTC",
        "gmt standard time" to "Europe/London",
        "greenwich standard time" to "Atlantic/Reykjavik",
        "morocco standard time" to "Africa/Casablanca",
        "w. europe standard time" to "Europe/Berlin",
        "central europe standard time" to "Europe/Budapest",
        "romance standard time" to "Europe/Paris",
        "central european standard time" to "Europe/Warsaw",
        "w. central africa standard time" to "Africa/Lagos",
        "namibia standard time" to "Africa/Windhoek",
        "gtb standard time" to "Europe/Bucharest",
        "middle east standard time" to "Asia/Beirut",
        "egypt standard time" to "Africa/Cairo",
        "e. europe standard time" to "Europe/Chisinau",
        "fle standard time" to "Europe/Kiev",
        "turkey standard time" to "Europe/Istanbul",
        "israel standard time" to "Asia/Jerusalem",
        "south africa standard time" to "Africa/Johannesburg",
        "arab standard time" to "Asia/Riyadh",
        "arabic standard time" to "Asia/Baghdad",
        "e. africa standard time" to "Africa/Nairobi",
        "russian standard time" to "Europe/Moscow",
        "iran standard time" to "Asia/Tehran",
        "arabian standard time" to "Asia/Dubai",
        "azerbaijan standard time" to "Asia/Baku",
        "caucasus standard time" to "Asia/Yerevan",
        "afghanistan standard time" to "Asia/Kabul",
        "west asia standard time" to "Asia/Tashkent",
        "pakistan standard time" to "Asia/Karachi",
        "india standard time" to "Asia/Calcutta",
        "sri lanka standard time" to "Asia/Colombo",
        "nepal standard time" to "Asia/Katmandu",
        "central asia standard time" to "Asia/Almaty",
        "bangladesh standard time" to "Asia/Dhaka",
        "myanmar standard time" to "Asia/Rangoon",
        "se asia standard time" to "Asia/Bangkok",
        "china standard time" to "Asia/Shanghai",
        "singapore standard time" to "Asia/Singapore",
        "w. australia standard time" to "Australia/Perth",
        "taipei standard time" to "Asia/Taipei",
        "tokyo standard time" to "Asia/Tokyo",
        "korea standard time" to "Asia/Seoul",
        "cen. australia standard time" to "Australia/Adelaide",
        "aus central standard time" to "Australia/Darwin",
        "e. australia standard time" to "Australia/Brisbane",
        "aus eastern standard time" to "Australia/Sydney",
        "tasmania standard time" to "Australia/Hobart",
        "west pacific standard time" to "Pacific/Port_Moresby",
        "central pacific standard time" to "Pacific/Guadalcanal",
        "new zealand standard time" to "Pacific/Auckland",
        "fiji standard time" to "Pacific/Fiji",
        "tonga standard time" to "Pacific/Tongatapu",
        "samoa standard time" to "Pacific/Apia"
    )
}
