// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Reminders
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeRole
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CalendarMappingTest {
    @Test
    fun `access levels map to the domain levels and unknown ones fall to the level below`() {
        val expected = mapOf(
            -1 to CalendarAccess.NONE,
            0 to CalendarAccess.NONE,
            100 to CalendarAccess.FREE_BUSY,
            200 to CalendarAccess.READ,
            300 to CalendarAccess.RESPOND,
            400 to CalendarAccess.RESPOND,
            500 to CalendarAccess.CONTRIBUTE,
            600 to CalendarAccess.EDIT,
            700 to CalendarAccess.OWNER,
            800 to CalendarAccess.OWNER,
            250 to CalendarAccess.READ
        )
        expected.forEach { (level, access) ->
            assertEquals(access, CalendarMapping.accessOf(level), "level $level")
        }
    }

    @Test
    fun `a calendar row becomes a calendar with an opaque color and a normalized owner`() {
        val row = mapOf(
            Calendars._ID to 7L,
            Calendars.ACCOUNT_NAME to "ana@gmail.com",
            Calendars.ACCOUNT_TYPE to "com.google",
            Calendars.CALENDAR_DISPLAY_NAME to "Work",
            Calendars.CALENDAR_COLOR to 0x0B63CE,
            Calendars.CALENDAR_ACCESS_LEVEL to 700L,
            Calendars.VISIBLE to 0L,
            Calendars.OWNER_ACCOUNT to " Ana@Gmail.com "
        )

        val calendar = requireNotNull(CalendarMapping.toCalendar(row))

        assertEquals(CalendarId(7), calendar.id)
        assertEquals(CalendarAccount("ana@gmail.com", "com.google"), calendar.account)
        assertEquals("Work", calendar.displayName)
        assertEquals(0xFF0B63CE.toInt(), calendar.color)
        assertEquals(CalendarAccess.OWNER, calendar.access)
        assertEquals(false, calendar.visible)
        assertEquals("ana@gmail.com", calendar.ownerEmail)
    }

    @Test
    fun `a calendar without an id or an owner is handled`() {
        assertNull(CalendarMapping.toCalendar(mapOf(Calendars.CALENDAR_DISPLAY_NAME to "x")))

        val bare = requireNotNull(CalendarMapping.toCalendar(mapOf(Calendars._ID to 1L)))
        assertNull(bare.ownerEmail)
        assertEquals(CalendarAccess.NONE, bare.access)
        assertEquals("", bare.account.name)
    }
}

class AttendeeMappingTest {
    @Test
    fun `every status survives a round trip and none or invited mean not answered`() {
        AttendeeStatus.entries.forEach {
            assertEquals(it, AttendeeMapping.statusOf(AttendeeMapping.statusCode(it)))
        }
        assertEquals(
            AttendeeStatus.NEEDS_ACTION,
            AttendeeMapping.statusOf(Attendees.ATTENDEE_STATUS_NONE)
        )
        assertEquals(AttendeeStatus.NEEDS_ACTION, AttendeeMapping.statusOf(null))
    }

    @Test
    fun `every role survives a round trip and none is required`() {
        AttendeeRole.entries.forEach {
            assertEquals(it, AttendeeMapping.roleOf(AttendeeMapping.roleCode(it)))
        }
        assertEquals(AttendeeRole.REQUIRED, AttendeeMapping.roleOf(Attendees.TYPE_NONE))
    }

    @Test
    fun `an attendee row is read and an organizer is flagged`() {
        val row = mapOf(
            Attendees.ATTENDEE_EMAIL to " Ana@Example.com",
            Attendees.ATTENDEE_NAME to "Ana",
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_ORGANIZER.toLong(),
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_OPTIONAL.toLong(),
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_TENTATIVE.toLong()
        )

        assertEquals(
            Attendee.of(
                "ana@example.com",
                "Ana",
                AttendeeRole.OPTIONAL,
                AttendeeStatus.TENTATIVE,
                isOrganizer = true
            ),
            AttendeeMapping.toAttendee(row)
        )
    }

    @Test
    fun `an attendee without an address or a name is skipped or unnamed`() {
        assertNull(AttendeeMapping.toAttendee(mapOf(Attendees.ATTENDEE_EMAIL to "  ")))
        assertNull(AttendeeMapping.toAttendee(mapOf(Attendees.ATTENDEE_NAME to "Ana")))
        val unnamed = AttendeeMapping.toAttendee(
            mapOf(Attendees.ATTENDEE_EMAIL to "a@b.c", Attendees.ATTENDEE_NAME to " ")
        )
        assertNull(unnamed?.name)
        assertEquals(false, unnamed?.isOrganizer)
    }

    @Test
    fun `an attendee is written with its relationship and an optional event id`() {
        val guest = Attendee.of("a@b.c", "A", AttendeeRole.RESOURCE, AttendeeStatus.NEEDS_ACTION)
        val organizer = Attendee.of("o@b.c", isOrganizer = true)

        val written = AttendeeMapping.toValues(guest, eventId = 5)
        assertEquals(5L, written[Attendees.EVENT_ID])
        assertEquals(Attendees.RELATIONSHIP_ATTENDEE, written[Attendees.ATTENDEE_RELATIONSHIP])
        assertEquals(Attendees.TYPE_RESOURCE, written[Attendees.ATTENDEE_TYPE])
        assertEquals(Attendees.ATTENDEE_STATUS_INVITED, written[Attendees.ATTENDEE_STATUS])

        val byReference = AttendeeMapping.toValues(organizer, eventId = null)
        assertEquals(false, byReference.containsKey(Attendees.EVENT_ID))
        assertEquals(Attendees.RELATIONSHIP_ORGANIZER, byReference[Attendees.ATTENDEE_RELATIONSHIP])
    }
}

class ReminderMappingTest {
    @Test
    fun `methods survive a round trip and the default and alarm are alerts`() {
        ReminderMethod.entries.forEach {
            assertEquals(it, ReminderMapping.methodOf(ReminderMapping.methodCode(it)))
        }
        assertEquals(ReminderMethod.ALERT, ReminderMapping.methodOf(Reminders.METHOD_DEFAULT))
        assertEquals(ReminderMethod.ALERT, ReminderMapping.methodOf(Reminders.METHOD_ALARM))
        assertEquals(ReminderMethod.ALERT, ReminderMapping.methodOf(null))
    }

    @Test
    fun `a reminder row is read and the calendar default is skipped`() {
        val row = mapOf(
            Reminders.MINUTES to 30L,
            Reminders.METHOD to Reminders.METHOD_EMAIL.toLong()
        )
        assertEquals(Reminder(30, ReminderMethod.EMAIL), ReminderMapping.toReminder(row))

        assertNull(
            ReminderMapping.toReminder(
                mapOf(Reminders.MINUTES to Reminders.MINUTES_DEFAULT.toLong())
            )
        )
        assertNull(ReminderMapping.toReminder(emptyMap()))
    }

    @Test
    fun `a reminder is written with its method and an optional event id`() {
        val written = ReminderMapping.toValues(Reminder(10, ReminderMethod.SMS), eventId = 3)
        assertEquals(
            mapOf(
                Reminders.EVENT_ID to 3L,
                Reminders.MINUTES to 10,
                Reminders.METHOD to Reminders.METHOD_SMS
            ),
            written
        )
        assertEquals(
            false,
            ReminderMapping.toValues(Reminder(0), null).containsKey(Reminders.EVENT_ID)
        )
    }
}

class ProviderRowTest {
    @Test
    fun `typed reads tolerate missing and mismatched columns`() {
        val row = mapOf("n" to 5L, "s" to "x", "z" to 0L, "d" to 2.5)

        assertEquals(5L, row.long("n"))
        assertEquals(5, row.int("n"))
        assertNull(row.long("s"))
        assertNull(row.text("n"))
        assertEquals("x", row.text("s"))
        assertEquals(true, row.flag("n"))
        assertEquals(false, row.flag("z"))
        assertEquals(false, row.flag("missing"))
        assertEquals(2L, row.long("d"))
        assertNull(row.int("missing"))
    }
}
