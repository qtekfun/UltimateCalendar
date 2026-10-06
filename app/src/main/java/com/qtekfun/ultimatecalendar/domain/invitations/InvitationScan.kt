// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus

/** What a scan knows about one event, enough to explain why an invitation went away. */
data class EventState(
    val isFuture: Boolean,
    /** The user's own answer, or null when the user is not an attendee. */
    val myStatus: AttendeeStatus?
)

/** The result of one run of [InvitationDetector.scan]. */
data class InvitationScan(
    /** Pending invitations, soonest first. */
    val pending: List<Invitation>,
    /** Every event seen in the run, to tell a deleted event from an answered one. */
    val states: Map<InvitationKey, EventState>
)
