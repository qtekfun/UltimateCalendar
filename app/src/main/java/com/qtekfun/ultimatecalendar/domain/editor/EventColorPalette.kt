// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo

/** The colors an event can be given besides its calendar's (ARGB), named as Google names them. */
enum class EventColorChoice(val argb: Int) {
    TOMATO(0xFFD50000.toInt()),
    FLAMINGO(0xFFE67C73.toInt()),
    TANGERINE(0xFFF4511E.toInt()),
    BANANA(0xFFF6BF26.toInt()),
    SAGE(0xFF33B679.toInt()),
    BASIL(0xFF0B8043.toInt()),
    PEACOCK(0xFF039BE5.toInt()),
    BLUEBERRY(0xFF3F51B5.toInt()),
    LAVENDER(0xFF7986CB.toInt()),
    GRAPE(0xFF8E24AA.toInt()),
    GRAPHITE(0xFF616161.toInt())
}

/** Whether a calendar takes a color of its own for each event (RF-05). */
object EventColorSupport {
    private const val GOOGLE_ACCOUNT = "com.google"

    /**
     * Google calendars keep an event's color as a key into their own palette (`EVENT_COLOR_KEY`),
     * which the provider does not let a client choose freely, and the sync adapter ignores a raw
     * color: there the field is hidden. Local and CalDAV calendars (DAVx5) store the raw color.
     */
    fun supports(calendar: CalendarInfo?): Boolean =
        calendar != null && calendar.account.type != GOOGLE_ACCOUNT
}
