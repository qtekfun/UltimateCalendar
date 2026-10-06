// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

/** A calendar of a source (a `Calendars._ID` for the Android provider). */
@JvmInline
value class CalendarId(val value: Long)

/** An event of a source (an `Events._ID` for the Android provider). */
@JvmInline
value class EventId(val value: Long)
