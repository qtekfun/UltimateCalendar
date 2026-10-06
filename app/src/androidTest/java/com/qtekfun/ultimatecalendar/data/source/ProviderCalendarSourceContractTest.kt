// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatecalendar.data.source.provider.ContentResolverGateway
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The contract suite against the real calendar provider. It creates its own LOCAL account with two
 * calendars (the only place `CALLER_IS_SYNCADAPTER` is used) and deletes them afterwards; it never
 * reads or writes any other calendar: instances are limited to the two test calendars. Run it with
 * `adb shell am instrument` (see CLAUDE.md), never `connectedDebugAndroidTest` on a personal phone.
 */
@RunWith(Parameterized::class)
class ProviderCalendarSourceContractTest(private val scenario: Scenario) {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var underTest: SourceUnderTest

    @Before
    fun setUp() {
        grantCalendarPermissions()
        deleteTestCalendars()
        val writable = createCalendar("Mine", Calendars.CAL_ACCESS_OWNER, CalendarAccess.OWNER)
        val readOnly = createCalendar("Holidays", Calendars.CAL_ACCESS_READ, CalendarAccess.READ)
        val source = ProviderCalendarSource(ContentResolverGateway(context), Dispatchers.IO)
        underTest = SourceUnderTest(
            ScopedSource(source, setOf(writable.id, readOnly.id), ::markAsSynced),
            writable,
            readOnly
        )
    }

    @After
    fun tearDown() = deleteTestCalendars()

    /** Grants the calendar permissions through the shell and checks that they took effect. */
    private fun grantCalendarPermissions() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).forEach {
            val output = ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("pm grant ${context.packageName} $it")
            ).use { stream -> stream.readBytes().decodeToString() }
            check(context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED) {
                "could not grant $it to ${context.packageName}: $output"
            }
        }
    }

    @Test
    fun scenarioHolds() = runBlocking { scenario.run(underTest) }

    private fun createCalendar(name: String, level: Int, access: CalendarAccess): CalendarInfo {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, name)
            put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, level)
            put(Calendars.OWNER_ACCOUNT, OWNER)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, "Europe/Madrid")
        }
        val uri =
            requireNotNull(
                context.contentResolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)
            )
        return CalendarInfo(
            id = CalendarId(ContentUris.parseId(uri)),
            account = CalendarAccount(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL),
            displayName = name,
            color = COLOR,
            access = access,
            ownerEmail = OWNER
        )
    }

    /** Deletes the calendars of the test account only, with their events. */
    private fun deleteTestCalendars() {
        context.contentResolver.delete(
            asSyncAdapter(Calendars.CONTENT_URI),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL)
        )
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    /**
     * Gives an event the `_SYNC_ID` that every event of a synced account (Google, DAVx5) has. The
     * provider matches the exceptions of a series to it: without one (only possible in a LOCAL
     * account) a series loses all its instances as soon as one occurrence is changed or cancelled
     * (seen on API 26 and 36). Test setup only: the app never writes sync columns.
     */
    private fun markAsSynced(id: EventId) {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, id.value)
        val values = ContentValues().apply { put(Events._SYNC_ID, "contract-${id.value}") }
        context.contentResolver.update(asSyncAdapter(uri), values, null, null)
    }

    /** Keeps "all calendars" to the two test calendars, so other calendars cannot disturb a scenario. */
    private class ScopedSource(
        private val inner: CalendarSource,
        private val ids: Set<CalendarId>,
        private val onCreated: (EventId) -> Unit
    ) : CalendarSource by inner {
        override suspend fun create(draft: EventDraft): CalendarResult<EventId> =
            inner.create(draft).also { result -> result.getOrNull()?.let(onCreated) }

        override suspend fun instances(
            range: TimeRange,
            calendarIds: Set<CalendarId>?
        ): CalendarResult<List<EventInstance>> = inner.instances(range, calendarIds ?: ids)

        override suspend fun search(
            query: String,
            calendarIds: Set<CalendarId>?,
            range: TimeRange?
        ): CalendarResult<List<SearchableEvent>> = inner.search(query, calendarIds ?: ids, range)
    }

    companion object {
        private const val ACCOUNT = "uc-contract-tests@example.invalid"
        private const val OWNER = "me@example.com"
        private const val COLOR = 0xFF0B63CE.toInt()

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun scenarios(): List<Array<Any>> = CalendarSourceContract.scenarios.map { arrayOf(it) }
    }
}
