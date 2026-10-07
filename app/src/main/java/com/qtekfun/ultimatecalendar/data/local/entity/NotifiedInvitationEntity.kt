// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.Entity

/**
 * An invitation already notified (RF-07), as it was when the user was told: the next check
 * notifies it again only if it differs from this. Keyed by calendar, event and [address]: blank for
 * the account of the calendar, else another of the user's accounts the event invites. [account] is
 * the address the invitation is for, to say so on screen. [start] and [end]
 * are epoch milliseconds for a timed event (with its [zone]) and epoch days for an all-day one
 * (no zone).
 */
@Entity(tableName = "notified_invitations", primaryKeys = ["calendarId", "eventId", "address"])
data class NotifiedInvitationEntity(
    val calendarId: Long,
    val eventId: Long,
    val title: String,
    val allDay: Boolean,
    val start: Long,
    val end: Long,
    val zone: String?,
    val location: String?,
    val organizer: String?,
    val address: String = "",
    val account: String? = null
)
