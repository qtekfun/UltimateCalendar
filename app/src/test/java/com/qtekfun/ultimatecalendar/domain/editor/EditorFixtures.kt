// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Shared invented data of the editor tests. */
internal object EditorFixtures {
    val madrid: ZoneId = ZoneId.of("Europe/Madrid")
    val newYork: ZoneId = ZoneId.of("America/New_York")
    val utc: ZoneId = ZoneId.of("UTC")

    /** 2026-03-10 10:07:30 in Madrid (CET, UTC+1). */
    val clock: Clock = Clock.fixed(Instant.parse("2026-03-10T09:07:30Z"), utc)

    val google = CalendarAccount("me@example.com", "com.google")
    val local = CalendarAccount("Phone", "LOCAL")
    val dav = CalendarAccount("Nextcloud", "bitfire.at.davdroid")

    val work = calendar(1, "Work", google)
    val phone = calendar(2, "Personal", local)
    val cloud = calendar(3, "Family", dav)

    fun calendar(
        id: Long,
        name: String,
        account: CalendarAccount,
        access: CalendarAccess = CalendarAccess.OWNER
    ) = CalendarInfo(
        id = CalendarId(id),
        account = account,
        displayName = name,
        color = 0xFF0B63CE.toInt(),
        access = access,
        ownerEmail = "me@example.com"
    )

    val defaults = EditorDefaults(
        durationMinutes = 60,
        timedReminders = listOf(10),
        allDayReminders = listOf(0)
    )

    /** A new timed event on 2026-03-10 from 10:30 to 11:30 in Madrid, in [work]. */
    fun form(
        start: LocalDateTime = LocalDateTime.parse("2026-03-10T10:30"),
        end: LocalDateTime = LocalDateTime.parse("2026-03-10T11:30"),
        zone: ZoneId = madrid
    ) = EventForm(
        start = start.atZone(zone),
        end = end.atZone(zone),
        zone = zone,
        calendarId = work.id,
        reminders = defaults.reminders(allDay = false),
        defaults = defaults
    )
}
