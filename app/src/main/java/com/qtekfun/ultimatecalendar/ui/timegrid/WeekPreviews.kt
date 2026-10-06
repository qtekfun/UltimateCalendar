// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridLayout
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridPage
import com.qtekfun.ultimatecalendar.ui.theme.UltimateCalendarTheme

// Invented data only; the helpers are those of the Day previews.
private fun weekPreviewPage(): TimeGridPage {
    val events = listOf(
        allDay(1, "Sample trip", previewDay.minusDays(1), previewDay.plusDays(3)),
        allDay(2, "Holiday", previewDay, previewDay.plusDays(1)),
        allDay(3, "Birthday", previewDay, previewDay.plusDays(1)),
        allDay(4, "Deadline", previewDay, previewDay.plusDays(1)),
        timed(5, "Planning", at(0, 9), at(0, 10, 30), BLUE),
        timed(6, "Coffee", at(0, 9, 30), at(0, 10), ORANGE),
        timed(7, "Invitation to a talk", at(1, 11), at(1, 12), BLUE, AttendeeStatus.NEEDS_ACTION),
        timed(8, "Late shift", at(2, 22), at(3, 2), GREEN),
        timed(9, "Review", at(4, 13), at(4, 14), ORANGE)
    )
    // A week starting on Monday 9 March 2026, with the Wednesday as today.
    val monday = previewDay.minusDays(2)
    return TimeGridLayout.build(DateRange(monday, monday.plusDays(7)), previewZone, events)
}

@Composable
private fun WeekPreview(weekNumber: Int? = 11) {
    UltimateCalendarTheme {
        TimeGridPageContent(
            page = weekPreviewPage(),
            failed = false,
            now = GridNow(at(0, 10, 15).atZone(previewZone).toInstant(), previewZone),
            scroll = rememberScrollState(),
            callbacks = GridCallbacks(),
            options = GridOptions(weekNumber = weekNumber, allDayRowLimit = 3)
        )
    }
}

@Preview(showBackground = true, heightDp = 640, widthDp = 411)
@Composable
private fun WeekPreviewDefault() = WeekPreview()

@Preview(showBackground = true, heightDp = 640, widthDp = 360, fontScale = 2f)
@Composable
private fun WeekLargeFontPreview() = WeekPreview(weekNumber = null)

@Preview(showBackground = true, heightDp = 640, widthDp = 320, uiMode = 32)
@Composable
private fun WeekNarrowDarkPreview() = WeekPreview()
