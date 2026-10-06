// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

class DiagTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun diag() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR").forEach {
            ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("pm grant ${context.packageName} $it")
            ).use { s -> s.readBytes() }
        }
        check(context.checkSelfPermission("android.permission.READ_CALENDAR") == PackageManager.PERMISSION_GRANTED)
        val report = StringBuilder()
        val cr = context.contentResolver
        fun sync(uri: android.net.Uri) = uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, "diag@example.invalid")
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, "LOCAL").build()
        cr.delete(sync(Calendars.CONTENT_URI), null, null)
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
        val calId = ContentUris.parseId(requireNotNull(cr.insert(sync(Calendars.CONTENT_URI), cal)))
        val start = 1_791_280_800_000L
        val ev = ContentValues().apply {
            put(Events.CALENDAR_ID, calId)
            put(Events.TITLE, "S")
            put(Events.DTSTART, start)
            put(Events.DURATION, "P3600S")
            put(Events.RRULE, "FREQ=DAILY;COUNT=3")
            put(Events.EVENT_TIMEZONE, "Europe/Madrid")
            put(Events.ALL_DAY, 0)
        }
        val evId = ContentUris.parseId(requireNotNull(cr.insert(Events.CONTENT_URI, ev)))
        fun dump(label: String) {
            report.append("== $label\n")
            cr.query(
                Events.CONTENT_URI,
                arrayOf(Events._ID, Events.TITLE, Events.STATUS, Events.ORIGINAL_ID, Events.ORIGINAL_INSTANCE_TIME, Events.DTSTART, Events.DTEND, Events.DURATION, Events.RRULE, Events.DELETED),
                "${Events.CALENDAR_ID}=?",
                arrayOf(calId.toString()),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    report.append((0 until c.columnCount).joinToString(" ") { c.getColumnName(it) + "=" + c.getString(it) }).append('\n')
                }
            }
            val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(b, start - 30 * 86_400_000)
            ContentUris.appendId(b, start + 60 * 86_400_000)
            cr.query(
                b.build(),
                arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE, Events.STATUS, Events.ORIGINAL_ID),
                null,
                null,
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    report.append("I " + (0 until c.columnCount).joinToString(" ") { c.getColumnName(it) + "=" + c.getString(it) }).append('\n')
                }
            }
        }
        dump("after create")
        try {
            val x = ContentValues().apply {
                put(Events.ORIGINAL_INSTANCE_TIME, start + 86_400_000)
                put(Events.STATUS, Events.STATUS_CANCELED)
            }
            val u = cr.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, evId), x)
            report.append("cancel inserted $u\n")
        } catch (e: Exception) {
            report.append("cancel failed ${e.javaClass.name}: ${e.message}\n")
        }
        dump("after cancel")
        Thread.sleep(2_000)
        dump("after cancel + 2s")
        try {
            val x = ContentValues().apply {
                put(Events.ORIGINAL_INSTANCE_TIME, start + 2 * 86_400_000)
                put(Events.TITLE, "Moved")
                put(Events.DTSTART, start + 2 * 86_400_000)
                put(Events.DURATION, "P1800S")
                put(Events.EVENT_TIMEZONE, "Europe/Madrid")
                put(Events.ALL_DAY, 0)
                put(Events.STATUS, Events.STATUS_CONFIRMED)
                put(Events.CALENDAR_ID, calId)
                put(Events.AVAILABILITY, 0)
                put(Events.HAS_ALARM, 0)
                put(Events.HAS_ATTENDEE_DATA, 0)
                putNull(Events.EVENT_LOCATION)
                putNull(Events.DESCRIPTION)
                putNull(Events.EVENT_COLOR)
            }
            val u = cr.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, evId), x)
            report.append("edit inserted $u\n")
        } catch (e: Exception) {
            report.append("edit failed ${e.javaClass.name}: ${e.message}\n")
        }
        dump("after edit")
        cr.delete(sync(Calendars.CONTENT_URI), null, null)
        throw AssertionError("DIAG\n$report")
    }
}
