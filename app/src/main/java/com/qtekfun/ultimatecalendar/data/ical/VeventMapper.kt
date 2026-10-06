// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Reads [VeventFields] from the `VEVENT`s of a `VCALENDAR` and writes them back. Writing compares
 * each field with what the file already says and only rewrites the properties that changed, so
 * the rest of the file, including what the app does not understand, stays byte for byte.
 */
object VeventMapper {
    const val PRODUCT_ID = "-//UltimateCalendar//UltimateCalendar//EN"

    /**
     * The fields of every readable `VEVENT` of [calendar]: the master first, then the overrides,
     * in file order. Floating times (no zone) are read in [floating].
     */
    fun read(calendar: IcsComponent, floating: ZoneId): List<VeventFields> {
        val zones = IcsZones.Resolver(calendar, floating)
        return calendar.components("VEVENT").mapNotNull { fields(it, zones) }
    }

    internal fun fields(vevent: IcsComponent, zones: IcsZones.Resolver): VeventFields? {
        val time = VeventTimes.read(vevent, zones) ?: return null
        val status = status(vevent)
        val organizer = VeventAttendees.organizer(vevent)
        return VeventFields(
            uid = vevent.property("UID")?.value?.trim().orEmpty(),
            title = vevent.property("SUMMARY")?.value?.let(IcsText::unescape).orEmpty(),
            time = time,
            location = text(vevent, "LOCATION"),
            description = text(vevent, "DESCRIPTION"),
            availability = availability(vevent, status),
            status = status,
            rrule = vevent.property("RRULE")?.value?.trim()?.takeIf { it.isNotEmpty() },
            exDates = VeventTimes.keys(vevent, "EXDATE", zones),
            rDates = VeventTimes.keys(vevent, "RDATE", zones),
            recurrenceId = VeventTimes.recurrenceId(vevent, zones),
            sequence = vevent.property("SEQUENCE")?.value?.trim()?.toIntOrNull() ?: 0,
            organizer = organizer,
            attendees = VeventAttendees.read(vevent, organizer),
            reminders = VeventAlarms.read(vevent, time, zones),
            modifiedAt = IcsDate.parseUtc(vevent.property("LAST-MODIFIED"))
        )
    }

    private fun text(vevent: IcsComponent, name: String): String? =
        vevent.property(name)?.value?.let(IcsText::unescape)?.takeIf { it.isNotEmpty() }

    private fun status(vevent: IcsComponent): EventStatus =
        when (vevent.property("STATUS")?.value?.trim()?.uppercase(Locale.ROOT)) {
            "CANCELLED" -> EventStatus.CANCELLED
            "TENTATIVE" -> EventStatus.TENTATIVE
            else -> EventStatus.CONFIRMED
        }

    /** TRANSP, Outlook's busy status and a tentative STATUS, in that order of precedence. */
    private fun availability(vevent: IcsComponent, status: EventStatus): Availability {
        val busy = vevent.property("X-MICROSOFT-CDO-BUSYSTATUS")?.value?.trim()
            ?.uppercase(Locale.ROOT)
        return when {
            vevent.property(
                "TRANSP"
            )?.value?.trim().equals("TRANSPARENT", true) -> Availability.FREE

            busy == "FREE" -> Availability.FREE

            busy == "TENTATIVE" || status == EventStatus.TENTATIVE -> Availability.TENTATIVE

            else -> Availability.BUSY
        }
    }

    /**
     * [base] with its `VEVENT`s updated to [events], or a new calendar when [base] is null. Each
     * event is matched with the existing `VEVENT` of the same occurrence ([VeventFields.recurrenceId],
     * null for the master); `VEVENT`s not listed are removed, new ones added after the others.
     * LAST-MODIFIED and DTSTAMP move to [now] and SEQUENCE grows on each event that changed.
     */
    fun write(
        base: IcsComponent?,
        events: List<VeventFields>,
        now: Instant,
        floating: ZoneId
    ): IcsComponent {
        val calendar = base ?: IcsComponent(
            "VCALENDAR",
            listOf(IcsProperty("VERSION", value = "2.0"), IcsProperty("PRODID", value = PRODUCT_ID))
        )
        val zones = IcsZones.Resolver(calendar, floating)
        val existing = calendar.components("VEVENT").map { it to fields(it, zones) }
        val changed = mutableListOf<VeventFields>()
        val written = events.map { event ->
            val old = existing.firstOrNull {
                it.second?.recurrenceId == event.recurrenceId &&
                    it.second != null
            }
            writeEvent(old?.first, old?.second, event, now, zones).also {
                if (it !== old?.first) changed += event
            }
        }
        // A VEVENT the app cannot read (no DTSTART) is not ours to drop.
        val unreadable = existing.filter { it.second == null }.map { it.first }
        val withEvents = calendar.withComponents("VEVENT", written + unreadable)
        return withTimeZones(withEvents, changed.flatMap { zoneNames(it.time) })
    }

    private fun writeEvent(
        old: IcsComponent?,
        before: VeventFields?,
        after: VeventFields,
        now: Instant,
        zones: IcsZones.Resolver
    ): IcsComponent {
        val start = old ?: IcsComponent(
            "VEVENT",
            listOf(
                IcsProperty("UID", value = after.uid),
                IcsProperty("CREATED", value = IcsDate.utc(now)),
                IcsProperty("DTSTAMP", value = IcsDate.utc(now))
            )
        )
        val updated = apply(start, before, after, zones)
        if (old != null && updated == old) return old
        return stamp(updated, isNew = old == null, now = now)
    }

    private fun apply(
        vevent: IcsComponent,
        before: VeventFields?,
        after: VeventFields,
        zones: IcsZones.Resolver
    ): IcsComponent {
        var result = vevent
        fun <T> field(get: (VeventFields) -> T, change: (IcsComponent, T) -> IcsComponent) {
            val value = get(after)
            if (before == null || get(before) != value) result = change(result, value)
        }
        field(VeventFields::title) { c, v -> c.withProperty("SUMMARY", textProperty("SUMMARY", v)) }
        field(VeventFields::description) { c, v ->
            c.withProperty("DESCRIPTION", v?.let { textProperty("DESCRIPTION", it) })
        }
        field(VeventFields::location) { c, v ->
            c.withProperty("LOCATION", v?.let { textProperty("LOCATION", it) })
        }
        field(VeventFields::time) { c, v -> VeventTimes.write(c, v) }
        field(VeventFields::rrule) { c, v ->
            c.withProperty("RRULE", v?.let { IcsProperty("RRULE", value = it) })
        }
        field(VeventFields::exDates) { c, v -> VeventTimes.withKeys(c, "EXDATE", v, after.time) }
        field(VeventFields::rDates) { c, v -> VeventTimes.withKeys(c, "RDATE", v, after.time) }
        field(VeventFields::recurrenceId) { c, v ->
            c.withProperty(
                "RECURRENCE-ID",
                v?.let { VeventTimes.recurrenceIdProperty(it, after.time) }
            )
        }
        field(VeventFields::availability) { c, v ->
            c.withProperty("TRANSP", IcsProperty("TRANSP", value = transparency(v)))
        }
        field({ effectiveStatus(it) }) { c, v ->
            c.withProperty("STATUS", IcsProperty("STATUS", value = v.name))
        }
        field(VeventFields::organizer) { c, v ->
            VeventAttendees.writeOrganizer(c, v, after.attendees)
        }
        field(VeventFields::attendees) { c, v -> VeventAttendees.write(c, after.organizer, v) }
        field(VeventFields::reminders) { c, v ->
            VeventAlarms.write(c, v, after.time, zones)
        }
        return result
    }

    private fun transparency(availability: Availability) =
        if (availability == Availability.FREE) "TRANSPARENT" else "OPAQUE"

    /** A tentative availability shows as a tentative STATUS, which is how iCalendar says it. */
    private fun effectiveStatus(fields: VeventFields): EventStatus =
        if (fields.status == EventStatus.CONFIRMED &&
            fields.availability == Availability.TENTATIVE
        ) {
            EventStatus.TENTATIVE
        } else {
            fields.status
        }

    private fun textProperty(name: String, value: String) =
        value.takeIf { it.isNotEmpty() }?.let { IcsProperty(name, value = IcsText.escape(it)) }

    private fun stamp(vevent: IcsComponent, isNew: Boolean, now: Instant): IcsComponent {
        val stamped = vevent
            .withProperty("LAST-MODIFIED", IcsProperty("LAST-MODIFIED", value = IcsDate.utc(now)))
            .withProperty("DTSTAMP", IcsProperty("DTSTAMP", value = IcsDate.utc(now)))
        if (isNew) return stamped
        val sequence = vevent.property("SEQUENCE")?.value?.trim()?.toIntOrNull() ?: 0
        return stamped.withProperty("SEQUENCE", IcsProperty("SEQUENCE", value = "${sequence + 1}"))
    }

    private fun zoneNames(time: EventTime): List<String> = listOfNotNull(VeventTimes.zoneName(time))

    /** Adds the VTIMEZONE of every zone in use that the file does not define yet. */
    private fun withTimeZones(calendar: IcsComponent, zones: List<String>): IcsComponent {
        val defined = calendar.components("VTIMEZONE").mapNotNull {
            it.property("TZID")?.value
        }.toSet()
        val missing = zones.filter { it != IcsDate.UTC && it !in defined }
            .distinct()
            .mapNotNull(VTimeZones::of)
        if (missing.isEmpty()) return calendar
        // Time zones go before the components that use them.
        val firstComponent = calendar.children.indexOfFirst { it is IcsComponent }
        val at = if (firstComponent < 0) calendar.children.size else firstComponent
        return calendar.copy(
            children = calendar.children.toMutableList().apply { addAll(at, missing) }
        )
    }

    /**
     * The time of the `VEVENT` of a cancelled occurrence: it starts at [key] (the zone of the
     * [master] if it is timed) and lasts no time, or one day.
     */
    internal fun placeholderTime(key: OccurrenceKey, master: EventTime): EventTime = when (key) {
        is OccurrenceKey.Day -> EventTime.AllDay(key.date, key.date.plusDays(1))

        is OccurrenceKey.Moment ->
            EventTime.Timed(key.at, key.at, (master as? EventTime.Timed)?.zone ?: ZoneOffset.UTC)
    }
}
