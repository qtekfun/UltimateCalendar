// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.agenda

import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the time of an event shows on one day of the agenda. */
sealed interface AgendaSlot {
    /** An all-day event, or a timed one that covers the whole day. */
    data object AllDay : AgendaSlot

    /** Starts and ends on this day. */
    data class Span(val start: Instant, val end: Instant) : AgendaSlot

    /** Starts on this day and goes on the next one (or has no length). */
    data class From(val start: Instant) : AgendaSlot

    /** Started on an earlier day and ends on this one. */
    data class Until(val end: Instant) : AgendaSlot
}

/** One event on one day of the agenda: a multi-day event has one entry per day it touches. */
data class AgendaEntry(
    val instance: EventInstance,
    val slot: AgendaSlot,
    /** ARGB: the event's own color, else its calendar's; null when neither is known. */
    val color: Int?,
    /** The event's own zone when its wall-clock time there differs from the device's. */
    val otherZone: ZoneId?
) {
    /** An unanswered invitation, drawn as an outline without fill (RF-03, RF-06). */
    val isPending: Boolean get() = instance.selfStatus?.isPending == true
}

/** A day of the agenda that has at least one event. */
data class AgendaDay(val date: LocalDate, val entries: List<AgendaEntry>)
