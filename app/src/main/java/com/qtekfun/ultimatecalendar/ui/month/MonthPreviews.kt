// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.month

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.month.MonthGrid
import com.qtekfun.ultimatecalendar.domain.month.MonthLayout
import com.qtekfun.ultimatecalendar.domain.month.MonthPage
import com.qtekfun.ultimatecalendar.ui.theme.ThemeOptions
import com.qtekfun.ultimatecalendar.ui.theme.UltimateCalendarTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

// Invented data only.
private val previewZone = ZoneId.of("Europe/Madrid")
private val previewToday = LocalDate.of(2026, 10, 6)
private const val BLUE = 0xFF3F51B5.toInt()
private const val GREEN = 0xFF0B8043.toInt()
private const val ORANGE = 0xFFF4511E.toInt()
private const val PURPLE = 0xFF8E24AA.toInt()

private fun day(dayOfMonth: Int): LocalDate = LocalDate.of(2026, 10, dayOfMonth)

private fun timed(
    id: Long,
    title: String,
    from: LocalDateTime,
    to: LocalDateTime,
    color: Int,
    status: AttendeeStatus? = null
) = EventInstance(
    eventId = EventId(id),
    calendarId = CalendarId(1),
    title = title,
    time = EventTime.Timed(
        from.atZone(previewZone).toInstant(),
        to.atZone(previewZone).toInstant(),
        previewZone
    ),
    color = color,
    selfStatus = status
)

private fun allDay(id: Long, title: String, from: LocalDate, to: LocalDate, color: Int) =
    EventInstance(
        eventId = EventId(id),
        calendarId = CalendarId(1),
        title = title,
        time = EventTime.AllDay(from, to),
        color = color
    )

private fun previewPage(): MonthPage {
    val events = listOf(
        allDay(1, "Sample trip", day(3), day(8), GREEN),
        allDay(2, "Holiday", day(12), day(13), PURPLE),
        timed(3, "Planning", day(6).atTime(9, 0), day(6).atTime(10, 30), BLUE),
        timed(4, "Coffee", day(6).atTime(10, 0), day(6).atTime(10, 30), ORANGE),
        timed(5, "Lunch", day(6).atTime(13, 0), day(6).atTime(14, 0), GREEN),
        timed(6, "Review", day(6).atTime(16, 0), day(6).atTime(17, 0), BLUE),
        timed(7, "Walk", day(6).atTime(18, 0), day(6).atTime(19, 0), ORANGE),
        timed(
            8,
            "Talk (invitation)",
            day(14).atTime(11, 0),
            day(14).atTime(12, 0),
            BLUE,
            AttendeeStatus.NEEDS_ACTION
        ),
        timed(
            9,
            "Maybe: dinner",
            day(15).atTime(20, 0),
            day(15).atTime(22, 0),
            PURPLE,
            AttendeeStatus.TENTATIVE
        ),
        timed(
            10,
            "Declined sync",
            day(15).atTime(9, 0),
            day(15).atTime(10, 0),
            ORANGE,
            AttendeeStatus.DECLINED
        ),
        allDay(11, "Conference", day(27), day(31), BLUE)
    )
    return MonthLayout.build(
        MonthGrid.of(YearMonth.of(2026, 10), DayOfWeek.MONDAY),
        previewZone,
        events
    )
}

@Composable
private fun MonthPreview(weekNumbers: Boolean = false) {
    UltimateCalendarTheme(ThemeOptions(dynamicColor = false)) {
        Surface {
            MonthPageContent(
                page = previewPage(),
                failed = false,
                context = MonthContext(previewToday, DayOfWeek.MONDAY, weekNumbers, previewZone),
                callbacks = MonthCallbacks()
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun MonthLightPreview() = MonthPreview()

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun MonthDarkPreview() = MonthPreview()

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun MonthWeekNumbersPreview() = MonthPreview(weekNumbers = true)

/** At 200% font the cells are too small for chips and show dots. */
@Preview(showBackground = true, widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun MonthLargeFontPreview() = MonthPreview()

/** A short window (landscape phone): fewer lanes, "+N" and dots. */
@Preview(showBackground = true, widthDp = 640, heightDp = 360)
@Composable
private fun MonthShortPreview() = MonthPreview()
