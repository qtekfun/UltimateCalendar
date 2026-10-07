// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.flows

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatecalendar.data.source.ProviderCalendarSource
import com.qtekfun.ultimatecalendar.data.source.provider.ContentResolverGateway
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** An event as the calendar provider stores it, read straight from its tables. */
data class StoredEvent(
    val id: Long,
    val calendarId: Long,
    val title: String?,
    val start: Long,
    val end: Long?,
    val rrule: String?,
    val originalId: Long?,
    val selfStatus: Int
)

/**
 * The calendars the UI flow tests run against: two writable calendars of a LOCAL test account in
 * the real calendar provider, created before and removed after each test. This is the only place
 * (besides the contract suite) that uses `CALLER_IS_SYNCADAPTER`, to create and delete the test
 * calendars and to give events the `_SYNC_ID` that every event of a synced account has: without
 * one, a LOCAL series loses all its instances once an occurrence is changed (T05). Nothing outside
 * the test account is ever read or written. What the app did is read back from the provider's
 * tables, not from the app's own source, so a test cannot agree with a bug by sharing it.
 */
class LocalCalendars(private val context: Context) {
    val zone: ZoneId = ZoneId.systemDefault()

    /** The address of the calendars' owner: an invitation to it is an invitation to "me". */
    val me = OWNER

    var work: CalendarId = CalendarId(0)
        private set
    var home: CalendarId = CalendarId(0)
        private set

    private val resolver get() = context.contentResolver
    private val source = ProviderCalendarSource(ContentResolverGateway(context), Dispatchers.IO)

    fun setUp() {
        grant(Manifest.permission.READ_CALENDAR)
        grant(Manifest.permission.WRITE_CALENDAR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grant(Manifest.permission.POST_NOTIFICATIONS)
        }
        tearDown()
        work = createCalendar(WORK_NAME)
        home = createCalendar(HOME_NAME)
    }

    /** Deletes the calendars of the test account, with their events. */
    fun tearDown() {
        resolver.delete(
            asSyncAdapter(Calendars.CONTENT_URI),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL)
        )
    }

    /** A title no other test (or earlier run) uses, so a flow only ever finds its own events. */
    fun unique(prefix: String): String =
        "$prefix ${UUID.randomUUID().toString().take(UNIQUE_CHARS)}"

    /** [hour]:00 tomorrow in the phone's zone: always in the future, whatever time the test runs. */
    fun tomorrowAt(hour: Int, minute: Int = 0): ZonedDateTime =
        LocalDate.now(zone).plusDays(1).atTime(hour, minute).atZone(zone)

    fun timed(
        title: String,
        calendar: CalendarId,
        start: ZonedDateTime,
        rrule: String? = null,
        attendees: List<Attendee> = emptyList(),
        reminders: List<Reminder> = emptyList()
    ) = EventDraft(
        calendarId = calendar,
        title = title,
        time = EventTime.Timed(start.toInstant(), start.plusHours(1).toInstant(), start.zone),
        rrule = rrule,
        attendees = attendees,
        reminders = reminders
    )

    /** Stores [draft] the way the app does, then marks it as synced (see the class comment). */
    fun seed(draft: EventDraft): EventId {
        val id = runBlocking { source.create(draft) }.getOrNull()
        checkNotNull(id) { "the provider did not accept the seeded event" }
        val values = ContentValues().apply { put(Events._SYNC_ID, "ui-flow-${id.value}") }
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, id.value)
        resolver.update(asSyncAdapter(uri), values, null, null)
        return id
    }

    /**
     * Makes [organizer] the organizer of [event], as the server of an invitation leaves it: a
     * locally created event is organised by the calendar's owner, and the owner's own changes are
     * not "changes by the organizer".
     */
    fun organizeBy(event: EventId, organizer: String) {
        val values = ContentValues().apply { put(Events.ORGANIZER, organizer) }
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, event.value)
        resolver.update(asSyncAdapter(uri), values, null, null)
    }

    /**
     * A calendar of the test account owned by another address, as a second Google account on the
     * phone is: the test account's calendars are the only ones ever touched, and [tearDown] removes
     * this one with the rest.
     */
    fun createOtherAccount(owner: String): CalendarId = createCalendar(OTHER_NAME, owner)

    /** Gives [event] the iCalendar UID a sync adapter stores, the same in every copy of it. */
    fun setUid(event: EventId, uid: String) {
        val values = ContentValues().apply { put(Events.UID_2445, uid) }
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, event.value)
        resolver.update(asSyncAdapter(uri), values, null, null)
    }

    /** The organizer moves [event] to [start] (an hour long): written as a sync adapter does. */
    fun moveTo(event: EventId, start: ZonedDateTime) {
        val values = ContentValues().apply {
            put(Events.DTSTART, start.toInstant().toEpochMilli())
            put(Events.DTEND, start.plusHours(1).toInstant().toEpochMilli())
        }
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, event.value)
        resolver.update(asSyncAdapter(uri), values, null, null)
    }

    /** Every event of the test calendars that is not deleted, series, exceptions and all. */
    fun events(): List<StoredEvent> {
        val projection = arrayOf(
            Events._ID,
            Events.CALENDAR_ID,
            Events.TITLE,
            Events.DTSTART,
            Events.DTEND,
            Events.RRULE,
            Events.ORIGINAL_ID,
            Events.SELF_ATTENDEE_STATUS
        )
        val cursor = resolver.query(
            Events.CONTENT_URI,
            projection,
            "${Events.CALENDAR_ID} IN (?, ?) AND ${Events.DELETED}=0",
            arrayOf(work.value.toString(), home.value.toString()),
            Events._ID
        )
        return checkNotNull(cursor).use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        StoredEvent(
                            id = it.getLong(0),
                            calendarId = it.getLong(1),
                            title = it.getString(2),
                            start = it.getLong(DTSTART),
                            end = if (it.isNull(DTEND)) null else it.getLong(DTEND),
                            rrule = it.getString(RRULE),
                            originalId = if (it.isNull(ORIGINAL)) null else it.getLong(ORIGINAL),
                            selfStatus = it.getInt(SELF_STATUS)
                        )
                    )
                }
            }
        }
    }

    fun eventsTitled(title: String): List<StoredEvent> = events().filter { it.title == title }

    /** The `ATTENDEE_STATUS_*` stored for [email] in [event], or null when it is not an attendee. */
    fun attendeeStatus(event: EventId, email: String): Int? {
        val cursor = resolver.query(
            Attendees.CONTENT_URI,
            arrayOf(Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_STATUS),
            "${Attendees.EVENT_ID}=?",
            arrayOf(event.value.toString()),
            null
        )
        return checkNotNull(cursor).use {
            var status: Int? = null
            while (it.moveToNext()) {
                if (it.getString(0).equals(email, ignoreCase = true)) status = it.getInt(1)
            }
            status
        }
    }

    /**
     * What a sync adapter looks at on [event]: `DIRTY` (the provider sets it when a client changes
     * the event or its attendees, whatever the account type) and the `_SYNC_ID` the app never
     * writes.
     */
    fun syncState(event: EventId): Pair<Int, String?> {
        val cursor = resolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, event.value),
            arrayOf(Events.DIRTY, Events._SYNC_ID),
            null,
            null,
            null
        )
        return checkNotNull(cursor).use {
            check(it.moveToFirst()) { "event ${event.value} is gone" }
            it.getInt(0) to it.getString(1)
        }
    }

    /** The minutes before of the reminders stored for [event], sorted. */
    fun reminderMinutes(event: Long): List<Int> {
        val cursor = resolver.query(
            Reminders.CONTENT_URI,
            arrayOf(Reminders.MINUTES),
            "${Reminders.EVENT_ID}=?",
            arrayOf(event.toString()),
            null
        )
        return checkNotNull(cursor).use {
            buildList { while (it.moveToNext()) add(it.getInt(0)) }.sorted()
        }
    }

    /** The start (epoch millis) of every instance, in the next days, of events titled [title]. */
    fun instanceStarts(title: String): List<Long> {
        val from = System.currentTimeMillis() - DAY_MS
        val cursor = Instances.query(
            resolver,
            arrayOf(Instances.TITLE, Instances.BEGIN),
            from,
            from + WINDOW_DAYS * DAY_MS
        )
        return checkNotNull(cursor).use {
            buildList {
                while (it.moveToNext()) if (it.getString(0) == title) add(it.getLong(1))
            }.sorted()
        }
    }

    private fun createCalendar(name: String, owner: String = OWNER): CalendarId {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, name)
            put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, owner)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, zone.id)
        }
        val uri = requireNotNull(resolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values))
        return CalendarId(ContentUris.parseId(uri))
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    /** Grants a runtime permission through the shell and checks that it took effect. */
    private fun grant(permission: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("pm grant ${context.packageName} $permission")
        ).use { it.readBytes().decodeToString() }
        check(context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            "could not grant $permission to ${context.packageName}: $output"
        }
    }

    private companion object {
        const val ACCOUNT = "uc-ui-flow-tests@example.invalid"
        const val OWNER = "me@example.com"
        const val WORK_NAME = "UI Work"
        const val HOME_NAME = "UI Home"
        const val OTHER_NAME = "UI Other account"
        const val COLOR = 0xFF0B63CE.toInt()
        const val UNIQUE_CHARS = 8
        const val DAY_MS = 86_400_000L
        const val WINDOW_DAYS = 14L

        // Column positions of the events projection above.
        const val DTSTART = 3
        const val DTEND = 4
        const val RRULE = 5
        const val ORIGINAL = 6
        const val SELF_STATUS = 7
    }
}
