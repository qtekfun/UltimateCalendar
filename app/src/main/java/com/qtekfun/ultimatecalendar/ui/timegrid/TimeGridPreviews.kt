// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridLayout
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridPage
import com.qtekfun.ultimatecalendar.ui.theme.UltimateCalendarTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// Invented data only.
internal val previewZone = ZoneId.of("Europe/Madrid")
internal val previewDay = LocalDate.of(2026, 3, 11)
internal const val BLUE = 0xFF3F51B5.toInt()
internal const val GREEN = 0xFF0B8043.toInt()
internal const val ORANGE = 0xFFF4511E.toInt()

internal fun timed(
    id: Long,
    title: String,
    from: LocalDateTime,
    to: LocalDateTime,
    color: Int,
    status: AttendeeStatus? = null,
    zone: ZoneId = previewZone
) = EventInstance(
    eventId = EventId(id),
    calendarId = CalendarId(1),
    title = title,
    time = EventTime.Timed(
        from.atZone(previewZone).toInstant(),
        to.atZone(previewZone).toInstant(),
        zone
    ),
    color = color,
    selfStatus = status
)

internal fun allDay(id: Long, title: String, from: LocalDate, to: LocalDate) = EventInstance(
    eventId = EventId(id),
    calendarId = CalendarId(1),
    title = title,
    time = EventTime.AllDay(from, to),
    color = GREEN
)

internal fun at(day: Long, hour: Int, minute: Int = 0): LocalDateTime =
    previewDay.plusDays(day).atTime(hour, minute)

private fun previewPage(days: Long): TimeGridPage {
    val events = listOf(
        allDay(1, "Sample trip", previewDay, previewDay.plusDays(2)),
        timed(2, "Planning", at(0, 9), at(0, 10, 30), BLUE),
        timed(3, "Coffee", at(0, 9, 30), at(0, 10), ORANGE),
        timed(4, "Invitation to a talk", at(0, 11), at(0, 12), BLUE, AttendeeStatus.NEEDS_ACTION),
        timed(
            5,
            "Call with another office",
            at(0, 15),
            at(0, 16),
            GREEN,
            zone = ZoneId.of("Asia/Tokyo")
        ),
        timed(6, "Review", at(1, 13), at(1, 14), ORANGE)
    )
    return TimeGridLayout.build(
        DateRange(previewDay, previewDay.plusDays(days)),
        previewZone,
        events
    )
}

@Composable
private fun GridPreview(days: Long) {
    UltimateCalendarTheme {
        TimeGridPageContent(
            page = previewPage(days),
            failed = false,
            now = GridNow(at(0, 10, 15).atZone(previewZone).toInstant(), previewZone),
            scroll = rememberScrollState(),
            callbacks = GridCallbacks()
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun DayPreview() = GridPreview(1)

@Preview(showBackground = true, heightDp = 640, fontScale = 2f)
@Composable
private fun DayLargeFontPreview() = GridPreview(1)

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun ThreeDaysPreview() = GridPreview(3)
