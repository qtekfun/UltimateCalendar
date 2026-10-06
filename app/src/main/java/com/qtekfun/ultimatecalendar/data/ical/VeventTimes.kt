// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** `DTSTART`, `DTEND`, `DURATION`, `EXDATE`, `RDATE` and `RECURRENCE-ID` of a `VEVENT`. */
internal object VeventTimes {
    private const val SECONDS_PER_DAY = 86_400L

    /** The time of [vevent], or null when it has no valid `DTSTART`. */
    fun read(vevent: IcsComponent, zones: IcsZones.Resolver): EventTime? {
        val start = vevent.property("DTSTART")?.let(IcsDate::from) ?: return null
        val end = vevent.property("DTEND")?.let(IcsDate::from)
        val duration = vevent.property("DURATION")?.value?.let(IcsDuration::parse)
        return if (start.allDay) {
            allDay(
                start,
                end,
                duration
            )
        } else {
            timed(start, end, duration, zones)
        }
    }

    private fun allDay(start: IcsDate, end: IcsDate?, duration: Long?): EventTime.AllDay {
        val first = LocalDate.parse(start.local)
        val last = when {
            end != null -> LocalDate.parse(end.local.substringBefore('T'))

            duration != null -> first.plusDays(
                maxOf(
                    1,
                    (duration + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY
                )
            )

            else -> first.plusDays(1)
        }
        return EventTime.AllDay(first, if (last.isAfter(first)) last else first.plusDays(1))
    }

    private fun timed(
        start: IcsDate,
        end: IcsDate?,
        duration: Long?,
        zones: IcsZones.Resolver
    ): EventTime.Timed {
        val from = zones.instant(start)
        val to = when {
            end != null -> zones.instant(end)
            duration != null -> from.plusSeconds(duration)
            else -> from
        }
        return EventTime.Timed(from, if (to.isBefore(from)) from else to, zones.zoneOf(start))
    }

    /** [vevent] with `DTSTART` and `DTEND` for [time] and no `DURATION`. */
    fun write(vevent: IcsComponent, time: EventTime): IcsComponent {
        val (start, end) = when (time) {
            is EventTime.AllDay -> IcsDate(time.startDate.toString()) to
                IcsDate(time.endDate.toString())

            is EventTime.Timed -> date(time.start, time.zone) to date(time.end, time.zone)
        }
        return vevent
            .withProperty("DTSTART", IcsDate.property("DTSTART", start))
            .withProperty("DTEND", IcsDate.property("DTEND", end))
            .withProperty("DURATION", null)
    }

    /** The zone a timed [time] is written in, or null for all-day events. */
    fun zoneName(time: EventTime): String? = (time as? EventTime.Timed)?.let { zoneName(it.zone) }

    private fun zoneName(zone: ZoneId): String = if (IcsZones.isUtc(zone)) IcsDate.UTC else zone.id

    private fun date(instant: Instant, zone: ZoneId): IcsDate {
        val name = zoneName(zone)
        val at = if (name == IcsDate.UTC) ZoneOffset.UTC else zone
        return IcsDate(LocalDateTime.ofInstant(instant, at).toString(), name)
    }

    /** The occurrences named by every [name] property (`EXDATE`, `RDATE`) of [vevent]. */
    fun keys(vevent: IcsComponent, name: String, zones: IcsZones.Resolver): Set<OccurrenceKey> =
        vevent.properties.filter { it.name.equals(name, true) }.flatMap { property ->
            property.value.split(',').mapNotNull { value ->
                // An RDATE period is "start/end" or "start/duration"; only the start counts.
                val single = property.copy(value = value.trim().substringBefore('/'), raw = null)
                IcsDate.from(single)?.let { key(it, zones) }
            }
        }.toSet()

    /** The occurrence of the `RECURRENCE-ID` of [vevent], if it is an override. */
    fun recurrenceId(vevent: IcsComponent, zones: IcsZones.Resolver): OccurrenceKey? =
        vevent.property("RECURRENCE-ID")?.let(IcsDate::from)?.let { key(it, zones) }

    private fun key(date: IcsDate, zones: IcsZones.Resolver): OccurrenceKey = if (date.allDay) {
        OccurrenceKey.Day(LocalDate.parse(date.local))
    } else {
        OccurrenceKey.Moment(zones.instant(date))
    }

    /** [vevent] with [keys] as its only [name] properties, in the zone of [time]. */
    fun withKeys(
        vevent: IcsComponent,
        name: String,
        keys: Set<OccurrenceKey>,
        time: EventTime
    ): IcsComponent {
        val days = keys.filterIsInstance<OccurrenceKey.Day>().map { it.date }.sorted()
        val moments = keys.filterIsInstance<OccurrenceKey.Moment>().map { it.at }.sorted()
        val zone = (time as? EventTime.Timed)?.zone ?: ZoneOffset.UTC
        val properties = listOfNotNull(
            days.takeIf { it.isNotEmpty() }?.let { dates ->
                IcsDate.property(name, IcsDate(dates.first().toString()))
                    .copy(value = dates.joinToString(",") { it.toString().replace("-", "") })
            },
            moments.takeIf { it.isNotEmpty() }?.let { instants ->
                val dates = instants.map { date(it, zone) }
                val first = IcsDate.property(name, dates.first())
                first.copy(value = dates.joinToString(",") { IcsDate.property(name, it).value })
            }
        )
        return vevent.withProperties(name, properties)
    }

    /** `RECURRENCE-ID` for [key], in the form of [time]'s `DTSTART`. */
    fun recurrenceIdProperty(key: OccurrenceKey, time: EventTime): IcsProperty = when (key) {
        is OccurrenceKey.Day -> IcsDate.property("RECURRENCE-ID", IcsDate(key.date.toString()))

        is OccurrenceKey.Moment -> IcsDate.property(
            "RECURRENCE-ID",
            date(key.at, (time as? EventTime.Timed)?.zone ?: ZoneOffset.UTC)
        )
    }
}
