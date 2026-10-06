// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * When an invitation is, as text for a notification or a row of the tray. Timed events are shown
 * in the phone's zone (like Google Calendar), so a call set in another zone reads at the local
 * hour; all-day events are dates and never move.
 */
object InvitationTimeText {
    fun format(time: EventTime, zone: ZoneId, locale: Locale): String {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val hour = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        return when (time) {
            is EventTime.AllDay -> if (time.lastDate == time.startDate) {
                date.format(time.startDate)
            } else {
                "${date.format(time.startDate)} – ${date.format(time.lastDate)}"
            }

            is EventTime.Timed -> {
                val start = time.start.atZone(zone)
                val end = time.end.atZone(zone)
                val to = if (end.toLocalDate() == start.toLocalDate()) {
                    hour.format(end)
                } else {
                    "${date.format(end)} ${hour.format(end)}"
                }
                "${date.format(start)} ${hour.format(start)} – $to"
            }
        }
    }
}
