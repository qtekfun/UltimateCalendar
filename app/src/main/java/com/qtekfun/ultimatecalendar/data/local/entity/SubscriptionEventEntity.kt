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
 * One event of a subscription (a master with its `RECURRENCE-ID` overrides), laid out like a
 * [DavEventEntity] so that `StoredSeries` reads and writes it and the `RecurrenceEngine` expands
 * it the same way. A subscription is read only and never reminds, so there are no attendees and
 * no reminders to keep; the rest of the columns are documented there. The pair
 * ([subscriptionId], [uid]) is the event's identity across downloads, so that its id is stable.
 */
@Entity(
    tableName = "subscription_event",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("subscriptionId", "uid", unique = true),
        Index("subscriptionId", "windowStart")
    ]
)
data class SubscriptionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    val uid: String,
    val title: String,
    val allDay: Boolean = false,
    val start: Long,
    val end: Long,
    val zone: String? = null,
    val windowStart: Long,
    val windowEnd: Long? = null,
    val location: String? = null,
    val description: String? = null,
    val availability: Availability = Availability.BUSY,
    val status: EventStatus = EventStatus.CONFIRMED,
    val sequence: Int = 0,
    val rrule: String? = null,
    val organizer: String? = null,
    @ColumnInfo(defaultValue = "[]") val exDates: String = "[]",
    @ColumnInfo(defaultValue = "[]") val rDates: String = "[]",
    @ColumnInfo(defaultValue = "[]") val overrides: String = "[]",
    @ColumnInfo(defaultValue = "NULL") val masterRecurrenceId: String? = null
)
