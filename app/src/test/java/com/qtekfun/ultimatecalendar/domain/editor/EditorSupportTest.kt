// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.cloud
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.dav
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.google
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.madrid
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.newYork
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.phone
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.work
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimeZonesTest {
    private val july = Instant.parse("2026-07-01T12:00:00Z")
    private val january = Instant.parse("2026-01-15T12:00:00Z")

    @Test
    fun `the phone's and the event's zones come first, once each`() {
        val options = TimeZones.options(july, Locale.ENGLISH, listOf(madrid, newYork, madrid))

        assertEquals(listOf(madrid, newYork), options.take(2).map { it.id })
        assertEquals(1, options.count { it.id == madrid })
    }

    @Test
    fun `the rest is ordered by offset then city`() {
        val rest = TimeZones.options(july, Locale.ENGLISH).map { it.offset.totalSeconds }

        assertEquals(rest.sorted(), rest)
    }

    @Test
    fun `legacy aliases and fixed-offset names are not listed`() {
        val ids = TimeZones.options(july, Locale.ENGLISH).map { it.id.id }

        assertFalse("US/Eastern" in ids)
        assertFalse(ids.any { it.startsWith("Etc/") })
        assertTrue("Europe/Madrid" in ids)
        assertTrue("UTC" in ids)
    }

    @Test
    fun `the offset is the one in force at the time of the event`() {
        val summer = TimeZones.options(july, Locale.ENGLISH, listOf(madrid)).first()
        val winter = TimeZones.options(january, Locale.ENGLISH, listOf(madrid)).first()

        assertEquals("GMT+02:00", summer.offsetLabel)
        assertEquals("GMT+01:00", winter.offsetLabel)
        assertEquals("Madrid", summer.city)
    }

    @Test
    fun `zero offset is just GMT`() {
        val utc = TimeZones.options(july, Locale.ENGLISH, listOf(ZoneId.of("UTC"))).first()

        assertEquals("GMT", utc.offsetLabel)
    }

    @Test
    fun `a city is found by a part of its name, in any case`() {
        val all = TimeZones.options(july, Locale.ENGLISH)

        assertTrue(TimeZones.search(all, "new yor").any { it.id == newYork })
        assertTrue(
            TimeZones.search(all, "BUENOS AIRES").any {
                it.id.id ==
                    "America/Argentina/Buenos_Aires"
            }
        )
    }

    @Test
    fun `a zone is found by its long name and its offset`() {
        val all = TimeZones.options(july, Locale.ENGLISH)

        assertTrue(TimeZones.search(all, "central european").any { it.id == madrid })
        assertTrue(TimeZones.search(all, "gmt+02:00").any { it.id == madrid })
    }

    @Test
    fun `every word of the query must match`() {
        val all = TimeZones.options(july, Locale.ENGLISH)

        assertTrue(TimeZones.search(all, "europe madrid").any { it.id == madrid })
        assertTrue(TimeZones.search(all, "europe tokyo").isEmpty())
    }

    @Test
    fun `a blank query shows everything and nonsense shows nothing`() {
        val all = TimeZones.options(july, Locale.ENGLISH)

        assertEquals(all, TimeZones.search(all, "  "))
        assertTrue(TimeZones.search(all, "qqqzzz").isEmpty())
    }
}

class EditScopesTest {
    private fun target(rrule: String?) = EditTarget(
        Event(
            EventId(1),
            work.id,
            "x",
            EventTime.Timed(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), madrid),
            rrule = rrule
        ),
        EventTime.Timed(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), madrid)
    )

    @Test
    fun `synced calendars offer all three choices`() {
        assertEquals(RecurrenceScope.entries, EditScopes.available(work))
        assertEquals(RecurrenceScope.entries, EditScopes.available(cloud))
    }

    @Test
    fun `local calendars cannot change one occurrence`() {
        assertEquals(
            listOf(RecurrenceScope.THIS_AND_FOLLOWING, RecurrenceScope.ALL),
            EditScopes.available(phone)
        )
    }

    @Test
    fun `no calendar yet still offers everything`() {
        assertEquals(RecurrenceScope.entries, EditScopes.available(null))
    }

    @Test
    fun `only a repeating event asks`() {
        assertTrue(EditScopes.needsChoice(target("FREQ=DAILY")))
        assertFalse(EditScopes.needsChoice(target(null)))
        assertFalse(EditScopes.needsChoice(null))
    }

    @Test
    fun `an on-device account is the LOCAL one`() {
        assertTrue(phone.account.isLocal)
        assertFalse(google.isLocal)
        assertFalse(dav.isLocal)
    }
}

class EditorCalendarsTest {
    private val readOnly = EditorFixtures.calendar(4, "Holidays", google, CalendarAccess.READ)
    private val hidden = EditorFixtures.calendar(5, "Hidden", google).copy(visible = false)

    @Test
    fun `only calendars that take new events are offered`() {
        val all = listOf(work, readOnly, phone)

        assertEquals(listOf(work, phone), EditorCalendars.writable(all))
    }

    @Test
    fun `the calendar chosen in Settings wins`() {
        val writable = listOf(work, phone, cloud)

        assertEquals(phone, EditorCalendars.initial(writable, phone.id, work.id))
    }

    @Test
    fun `then the repository's automatic choice`() {
        val writable = listOf(work, phone, cloud)

        assertEquals(cloud, EditorCalendars.initial(writable, CalendarId(99), cloud.id))
        assertEquals(cloud, EditorCalendars.initial(writable, null, cloud.id))
    }

    @Test
    fun `then the first visible calendar, then the first`() {
        assertEquals(work, EditorCalendars.initial(listOf(hidden, work), null, null))
        assertEquals(hidden, EditorCalendars.initial(listOf(hidden), null, null))
    }

    @Test
    fun `without calendars there is none`() {
        assertNull(EditorCalendars.initial(emptyList(), phone.id, work.id))
    }
}

class EventColorSupportTest {
    @Test
    fun `google calendars keep colors as palette keys, so the field is hidden`() {
        assertFalse(EventColorSupport.supports(work))
    }

    @Test
    fun `local and caldav calendars take a color for each event`() {
        assertTrue(EventColorSupport.supports(phone))
        assertTrue(EventColorSupport.supports(cloud))
    }

    @Test
    fun `no calendar, no color`() {
        assertFalse(EventColorSupport.supports(null))
    }

    @Test
    fun `the palette has distinct opaque colors`() {
        val colors = EventColorChoice.entries.map { it.argb }

        assertEquals(colors.size, colors.toSet().size)
        assertTrue(colors.all { (it ushr 24) == 0xFF })
    }
}

class ReminderInputTest {
    @Test
    fun `a timed event offers the usual offsets`() {
        assertTrue(0 in ReminderInput.choices(allDay = false))
        assertTrue(10 in ReminderInput.choices(allDay = false))
    }

    @Test
    fun `an all-day event offers whole days only`() {
        assertEquals(listOf(0, 1440, 2880, 10_080), ReminderInput.choices(allDay = true))
        assertEquals(
            listOf(ReminderUnit.DAYS, ReminderUnit.WEEKS),
            ReminderInput.units(allDay = true)
        )
        assertEquals(ReminderUnit.entries, ReminderInput.units(allDay = false))
    }

    @Test
    fun `an amount and a unit make minutes`() {
        assertEquals(45, ReminderInput.minutes(45, ReminderUnit.MINUTES))
        assertEquals(180, ReminderInput.minutes(3, ReminderUnit.HOURS))
        assertEquals(2880, ReminderInput.minutes(2, ReminderUnit.DAYS))
        assertEquals(20_160, ReminderInput.minutes(2, ReminderUnit.WEEKS))
        assertEquals(0, ReminderInput.minutes(0, ReminderUnit.DAYS))
    }

    @Test
    fun `more than four weeks, or less than nothing, is refused`() {
        assertEquals(40_320, ReminderInput.minutes(4, ReminderUnit.WEEKS))
        assertNull(ReminderInput.minutes(5, ReminderUnit.WEEKS))
        assertNull(ReminderInput.minutes(-1, ReminderUnit.MINUTES))
        assertNull(ReminderInput.minutes(Int.MAX_VALUE, ReminderUnit.WEEKS))
    }
}

class EditorRequestTest {
    @Test
    fun `a new event at a tapped time survives being saved`() {
        val request = EditorRequest.New(LocalDateTime.parse("2026-03-10T10:30"))

        assertEquals(request, EditorRequest.decode(request.encode()))
    }

    @Test
    fun `a new event with no time survives being saved`() {
        assertEquals(EditorRequest.New(), EditorRequest.decode(EditorRequest.New().encode()))
    }

    @Test
    fun `an edit of an occurrence survives being saved`() {
        val request = EditorRequest.Edit(EventRef(EventId(7), 1_000, 2_000, allDay = false))

        assertEquals(request, EditorRequest.decode(request.encode()))
    }

    @Test
    fun `anything else is no request`() {
        assertNull(EditorRequest.decode(null))
        assertNull(EditorRequest.decode(emptyList()))
        assertNull(EditorRequest.decode(listOf("other", "x")))
        assertNull(EditorRequest.decode(listOf("edit", "garbage")))
        assertNull(EditorRequest.decode(listOf("new")))
    }
}
