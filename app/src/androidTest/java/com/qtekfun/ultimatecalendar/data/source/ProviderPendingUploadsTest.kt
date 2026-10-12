// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatecalendar.data.source.provider.ContentResolverGateway
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderPendingUploads
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real provider marks an answer for upload and the mark goes once the sync adapter has
 * uploaded it: what the follow-up of answers and the detail rely on. A LOCAL account is ignored
 * on purpose (nothing uploads it), so the test plays a synced account of its own type, and plays
 * its sync adapter with `CALLER_IS_SYNCADAPTER`: to set up the invitation and to "upload" it.
 * It touches only its own calendar and deletes it afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ProviderPendingUploadsTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val gateway = ContentResolverGateway(context)
    private val source = ProviderCalendarSource(gateway, Dispatchers.IO)
    private val uploads = ProviderPendingUploads(gateway, Dispatchers.IO)
    private val account = CalendarAccount(ACCOUNT, TYPE)

    @Before
    fun setUp() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).forEach {
            automation.executeShellCommand("pm grant ${context.packageName} $it").close()
        }
        deleteTestCalendars()
    }

    @After
    fun tearDown() = deleteTestCalendars()

    @Test
    fun anAnswerIsPendingUntilTheSyncAdapterUploadsIt() = runBlocking {
        val event = invitation()
        assertFalse(uploads.isPending(event))
        assertFalse(account in uploads.accounts().orEmpty())

        assertEquals(CalendarResult.Success(Unit), source.respond(event, AttendeeStatus.ACCEPTED))
        assertTrue(uploads.isPending(event))
        assertTrue(account in uploads.accounts().orEmpty())

        uploaded(event)
        assertFalse(uploads.isPending(event))
        assertFalse(account in uploads.accounts().orEmpty())
    }

    /** An event that arrived by sync (not marked), with the user invited and not answered. */
    private fun invitation(): EventId {
        val calendar = ContentUris.parseId(
            insert(
                Calendars.CONTENT_URI,
                ContentValues().apply {
                    put(Calendars.ACCOUNT_NAME, ACCOUNT)
                    put(Calendars.ACCOUNT_TYPE, TYPE)
                    put(Calendars.NAME, "Invited")
                    put(Calendars.CALENDAR_DISPLAY_NAME, "Invited")
                    put(Calendars.CALENDAR_COLOR, COLOR)
                    put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                    put(Calendars.OWNER_ACCOUNT, ME)
                    put(Calendars.VISIBLE, 1)
                    put(Calendars.SYNC_EVENTS, 1)
                    put(Calendars.CALENDAR_TIME_ZONE, "UTC")
                }
            )
        )
        val start = System.currentTimeMillis() + DAY_MS
        val event = ContentUris.parseId(
            insert(
                Events.CONTENT_URI,
                ContentValues().apply {
                    put(Events.CALENDAR_ID, calendar)
                    put(Events.TITLE, "Lunch")
                    put(Events.DTSTART, start)
                    put(Events.DTEND, start + HOUR_MS)
                    put(Events.EVENT_TIMEZONE, "UTC")
                    put(Events.ORGANIZER, "organizer@example.com")
                    put(Events._SYNC_ID, "pending-uploads-test")
                    put(Events.DIRTY, 0)
                }
            )
        )
        insert(
            Attendees.CONTENT_URI,
            ContentValues().apply {
                put(Attendees.EVENT_ID, event)
                put(Attendees.ATTENDEE_EMAIL, ME)
                put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
                put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
                put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_INVITED)
            }
        )
        return EventId(event)
    }

    /** What the sync adapter does once the change is on the server. */
    private fun uploaded(event: EventId) {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, event.value)
        context.contentResolver.update(
            asSyncAdapter(uri),
            ContentValues().apply { put(Events.DIRTY, 0) },
            null,
            null
        )
    }

    private fun insert(uri: Uri, values: ContentValues): Uri =
        requireNotNull(context.contentResolver.insert(asSyncAdapter(uri), values))

    private fun deleteTestCalendars() {
        context.contentResolver.delete(
            asSyncAdapter(Calendars.CONTENT_URI),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT, TYPE)
        )
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, TYPE)
        .build()

    private companion object {
        const val ACCOUNT = "pending-uploads@example.com"
        const val TYPE = "com.qtekfun.ultimatecalendar.test"
        const val ME = "me@example.com"
        const val COLOR = 0xFF3366CC.toInt()
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
    }
}
