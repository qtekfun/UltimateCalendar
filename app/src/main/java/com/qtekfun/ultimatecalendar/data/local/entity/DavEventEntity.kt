// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.qtekfun.ultimatecalendar.data.ical.EventStatus
import com.qtekfun.ultimatecalendar.domain.model.Availability

/**
 * An event resource (one `.ics` with its master and its `RECURRENCE-ID` overrides), the local
 * source of truth for the CalDAV account. The master's fields the queries need are columns; the
 * lists that hang from the series (attendees, reminders, `EXDATE`, `RDATE`, overrides) are JSON
 * (see `StoredSeries`). [ics] keeps the last version read from the server, so properties the app
 * does not know survive every write and a three-way merge can tell which side changed what.
 *
 * [start] and [end] are epoch milliseconds for a timed event (with its [zone]) and epoch days
 * for an all-day one (no zone). [windowStart] and [windowEnd] bound every occurrence in epoch
 * milliseconds, generously for all-day events (whose zone is the viewer's); a null [windowEnd]
 * means the series has no known end.
 */
@Entity(
    tableName = "dav_event",
    foreignKeys = [
        ForeignKey(
            entity = DavCalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("accountId", "href", unique = true),
        Index("calendarId", "windowStart"),
        Index("accountId", "uid")
    ]
)
data class DavEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val calendarId: Long,
    /** The resource on the server; chosen by the app for new events, before they are uploaded. */
    val href: String,
    val uid: String,
    /** Null until the server has the event. */
    val etag: String? = null,
    /** The resource as last read from the server; null for events not uploaded yet. */
    val ics: String? = null,
    val title: String,
    val allDay: Boolean = false,
    val start: Long,
    val end: Long,
    val zone: String? = null,
    val windowStart: Long,
    val windowEnd: Long? = null,
    val location: String? = null,
    val description: String? = null,
    /** ARGB, only on this device; null uses the calendar's. */
    val color: Int? = null,
    val availability: Availability = Availability.BUSY,
    val status: EventStatus = EventStatus.CONFIRMED,
    val sequence: Int = 0,
    /** `RRULE` text, without the property name. */
    val rrule: String? = null,
    val organizer: String? = null,
    val modifiedAt: Long? = null,
    @ColumnInfo(defaultValue = "[]") val attendees: String = "[]",
    @ColumnInfo(defaultValue = "[]") val reminders: String = "[]",
    @ColumnInfo(defaultValue = "[]") val exDates: String = "[]",
    @ColumnInfo(defaultValue = "[]") val rDates: String = "[]",
    @ColumnInfo(defaultValue = "[]") val overrides: String = "[]",
    /** Set when the resource has only an override (an invitation to one occurrence): its key. */
    @ColumnInfo(defaultValue = "NULL") val masterRecurrenceId: String? = null,
    /** Fields changed here and not yet accepted by the server, as a bit set of `EventField`. */
    val dirtyFields: Int = 0,
    /** Deleted here, waiting for the server to delete it too. */
    val deleted: Boolean = false,
    /** The server title while the user chooses between it and the local one (SPEC §5, rule 1). */
    @ColumnInfo(defaultValue = "NULL") val conflictTitle: String? = null,
    @ColumnInfo(defaultValue = "NULL") val conflictDescription: String? = null,
    @ColumnInfo(defaultValue = "NULL") val conflictLocation: String? = null,
    /** Deleted on the server while changed here: keep a copy or discard it (SPEC §5, rule 3). */
    @ColumnInfo(defaultValue = "0") val deletedOnServer: Boolean = false
)
