// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

class DiagTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val report = StringBuilder()
    private val cr: ContentResolver = context.contentResolver
    private val start = 1_791_280_800_000L
    private val day = 86_400_000L

    private fun sync(uri: Uri) = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, "diag@example.invalid")
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, "LOCAL")
        .build()

    private fun calendar(): Long {
        val cal = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, "diag@example.invalid")
            put(Calendars.ACCOUNT_TYPE, "LOCAL")
            put(Calendars.NAME, "d")
            put(Calendars.CALENDAR_DISPLAY_NAME, "d")
            put(Calendars.CALENDAR_COLOR, -1)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, "me@example.com")
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }
        return ContentUris.parseId(requireNotNull(cr.insert(sync(Calendars.CONTENT_URI), cal)))
    }

    private fun instances(label: String, from: Long, to: Long, calId: Long) {
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, from)
        ContentUris.appendId(b, to)
        report.append("== $label\n")
        cr.query(
            b.build(),
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.BEGIN,
                Events.STATUS,
                Events.ORIGINAL_ID
            ),
            "${Events.CALENDAR_ID}=?",
            arrayOf(calId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                report.append(
                    "I " + (0 until c.columnCount).joinToString(" ") {
                        c.getColumnName(it) + "=" + c.getString(it)
                    } + "\n"
                )
            }
        }
    }

    private fun experiment(label: String, withSyncId: Boolean) {
        report.append("##### $label\n")
        val calId = calendar()
        val ev = ContentValues().apply {
            put(Events.CALENDAR_ID, calId)
            put(Events.TITLE, "S")
            put(Events.DTSTART, start)
            put(Events.DURATION, "P3600S")
            put(Events.RRULE, "FREQ=DAILY;COUNT=3")
            put(Events.EVENT_TIMEZONE, "Europe/Madrid")
            put(Events.ALL_DAY, 0)
            if (withSyncId) put(Events._SYNC_ID, "sync-1")
        }
        val uri = if (withSyncId) sync(Events.CONTENT_URI) else Events.CONTENT_URI
        val evId = ContentUris.parseId(requireNotNull(cr.insert(uri, ev)))
        instances("created", start - day, start + 10 * day, calId)
        val x = ContentValues().apply {
            put(Events.ORIGINAL_INSTANCE_TIME, start + day)
            put(Events.STATUS, Events.STATUS_CANCELED)
        }
        cr.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, evId), x)
        instances("cancelled, same range", start - day, start + 10 * day, calId)
        instances("cancelled, other range", start + 40 * day, start + 41 * day, calId)
        instances("cancelled, same range again", start - day, start + 10 * day, calId)
        instances("cancelled, wider range", start - 5 * day, start + 20 * day, calId)
    }

    @Test
    fun diag() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR").forEach {
            ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("pm grant ${context.packageName} $it")
            ).use { s -> s.readBytes() }
        }
        cr.delete(sync(Calendars.CONTENT_URI), null, null)
        try {
            experiment("client series", withSyncId = false)
            experiment("series with _sync_id", withSyncId = true)
        } catch (e: Exception) {
            report.append("FAILED ${e.javaClass.name}: ${e.message}\n")
        }
        cr.delete(sync(Calendars.CONTENT_URI), null, null)
        throw AssertionError("DIAG\n$report")
    }
}
