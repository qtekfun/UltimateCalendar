// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A DATE or DATE-TIME value as the app stores it: ISO local text (`2026-10-05` for all-day,
 * `2026-10-05T09:30` with time) and its [zone]: an IANA id, [UTC], or null for floating time.
 */
data class IcsDate(val local: String, val zone: String? = null) {
    val allDay: Boolean get() = 'T' !in local

    /** The instant this date stands for, reading floating and all-day values in [fallback]. */
    fun toInstant(fallback: ZoneId): Instant {
        val zoneId = zone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback
        return if (allDay) {
            LocalDate.parse(local).atStartOfDay(zoneId).toInstant()
        } else {
            LocalDateTime.parse(local).atZone(zoneId).toInstant()
        }
    }

    companion object {
        const val UTC = "UTC"

        private val BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val BASIC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

        /** Reads a DUE/DTSTART-like property, or null when its value is not a valid date. */
        fun from(property: IcsProperty): IcsDate? = runCatching {
            val value = property.value.trim()
            if (value.length == DATE_LENGTH) {
                IcsDate(LocalDate.parse(value, BASIC_DATE).toString())
            } else {
                val utc = value.endsWith('Z')
                val local = LocalDateTime.parse(value.removeSuffix("Z"), BASIC_DATE_TIME)
                IcsDate(local.toString(), if (utc) UTC else property.parameter("TZID")?.value)
            }
        }.getOrNull()

        /** A UTC DATE-TIME, as COMPLETED, LAST-MODIFIED and DTSTAMP require. */
        fun utc(instant: Instant): String =
            BASIC_DATE_TIME.format(instant.atOffset(ZoneOffset.UTC)) + "Z"

        fun parseUtc(property: IcsProperty?): Instant? = property?.let(::from)
            ?.takeIf { !it.allDay }
            ?.toInstant(ZoneOffset.UTC)

        /** The property [name] holding [date], with `VALUE=DATE` or `TZID` as needed. */
        fun property(name: String, date: IcsDate): IcsProperty = when {
            date.allDay -> IcsProperty(
                name,
                listOf(IcsParameter("VALUE", "DATE")),
                BASIC_DATE.format(LocalDate.parse(date.local))
            )

            else -> {
                val text = BASIC_DATE_TIME.format(LocalDateTime.parse(date.local))
                when (date.zone) {
                    null -> IcsProperty(name, value = text)
                    UTC -> IcsProperty(name, value = text + "Z")
                    else -> IcsProperty(name, listOf(IcsParameter("TZID", date.zone)), text)
                }
            }
        }

        private const val DATE_LENGTH = 8
    }
}
