// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.screenshots

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** One invented person at an event: [status] is an `Attendees.ATTENDEE_STATUS_*`. */
data class DemoGuest(val name: String, val email: String, val status: Int)

/** One invented event. All-day events set [allDay] and [days]; the others [start] and [minutes]. */
data class DemoEvent(
    val calendar: String,
    val title: String,
    val start: LocalDateTime? = null,
    val minutes: Int = 0,
    val allDay: LocalDate? = null,
    val days: Int = 1,
    val location: String? = null,
    val description: String? = null,
    val rrule: String? = null,
    val color: Int? = null,
    val organizer: DemoGuest? = null,
    val guests: List<DemoGuest> = emptyList(),
    /** The attendee status of the phone's owner, or null when they are not invited. */
    val me: Int? = null
)

/** A calendar of the demo account. */
data class DemoCalendar(val key: String, val name: String, val color: Int)

/**
 * Loads invented calendars and events into a LOCAL account of the real calendar provider and
 * removes them again. It is the only code besides the contract suite that uses
 * `CALLER_IS_SYNCADAPTER`, and it only touches its own account: it never reads or writes any
 * other calendar. Everything in it is made up (example.com addresses); nothing is read from the
 * device.
 */
class DemoProvider(private val context: Context, private val zone: ZoneId) {
    private val calendarIds = mutableMapOf<String, Long>()

    /** Creates [calendars] in the demo account, replacing what a previous run left. */
    fun createCalendars(calendars: List<DemoCalendar>) {
        clear()
        calendars.forEach { calendarIds[it.key] = insertCalendar(it) }
    }

    fun add(event: DemoEvent) {
        val id = insertEvent(event)
        val organizer = event.organizer?.let { it to Attendees.RELATIONSHIP_ORGANIZER }
        val others = event.guests.map { it to Attendees.RELATIONSHIP_ATTENDEE }
        val me = event.me?.let {
            DemoGuest(OWNER_NAME, OWNER, it) to Attendees.RELATIONSHIP_ATTENDEE
        }
        listOfNotNull(organizer, me).plus(others).forEach { (guest, relationship) ->
            insertAttendee(id, guest, relationship)
        }
    }

    /** Deletes the calendars of the demo account, with their events. */
    fun clear() {
        context.contentResolver.delete(
            asSyncAdapter(Calendars.CONTENT_URI),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL)
        )
        calendarIds.clear()
    }

    private fun insertCalendar(calendar: DemoCalendar): Long {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, calendar.key)
            put(Calendars.CALENDAR_DISPLAY_NAME, calendar.name)
            put(Calendars.CALENDAR_COLOR, calendar.color)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, OWNER)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, zone.id)
        }
        val uri = requireNotNull(
            context.contentResolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)
        )
        return ContentUris.parseId(uri)
    }

    private fun insertEvent(event: DemoEvent): Long {
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, checkNotNull(calendarIds[event.calendar]))
            put(Events.TITLE, event.title)
            put(Events.EVENT_LOCATION, event.location)
            put(Events.DESCRIPTION, event.description)
            put(Events.ORGANIZER, event.organizer?.email ?: OWNER)
            put(Events.HAS_ATTENDEE_DATA, if (event.guests.isEmpty() && event.me == null) 0 else 1)
            event.color?.let { put(Events.EVENT_COLOR, it) }
            putTimes(event)
        }
        val uri = requireNotNull(
            context.contentResolver.insert(asSyncAdapter(Events.CONTENT_URI), values)
        )
        return ContentUris.parseId(uri)
    }

    private fun ContentValues.putTimes(event: DemoEvent) {
        val day = event.allDay
        if (day != null) {
            put(Events.ALL_DAY, 1)
            put(Events.EVENT_TIMEZONE, "UTC")
            put(Events.DTSTART, day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            val end = day.plusDays(event.days.toLong()).atStartOfDay()
            put(Events.DTEND, end.toInstant(ZoneOffset.UTC).toEpochMilli())
            return
        }
        val start = checkNotNull(event.start).atZone(zone)
        put(Events.EVENT_TIMEZONE, zone.id)
        put(Events.DTSTART, start.toInstant().toEpochMilli())
        if (event.rrule == null) {
            put(Events.DTEND, start.plusMinutes(event.minutes.toLong()).toInstant().toEpochMilli())
        } else {
            // A repeating event has a duration, not an end.
            put(Events.RRULE, event.rrule)
            put(Events.DURATION, "PT${event.minutes}M")
        }
    }

    private fun insertAttendee(eventId: Long, guest: DemoGuest, relationship: Int) {
        val values = ContentValues().apply {
            put(Attendees.EVENT_ID, eventId)
            put(Attendees.ATTENDEE_NAME, guest.name)
            put(Attendees.ATTENDEE_EMAIL, guest.email)
            put(Attendees.ATTENDEE_RELATIONSHIP, relationship)
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_STATUS, guest.status)
        }
        context.contentResolver.insert(asSyncAdapter(Attendees.CONTENT_URI), values)
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    companion object {
        /** The demo account: its name shows in the drawer, so it is a believable invented one. */
        const val ACCOUNT = "ana.garcia@example.com"
        const val OWNER = ACCOUNT
        const val OWNER_NAME = "Ana Garcia"
    }
}
