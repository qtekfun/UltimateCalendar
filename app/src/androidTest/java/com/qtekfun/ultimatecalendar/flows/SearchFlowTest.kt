// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.flows

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/** Flow 6: search finds a seeded event and opens it. */
@HiltAndroidTest
class SearchFlowTest : FlowTest() {
    @Test
    fun searchFindsAnEventByItsTitleAndOpensIt() {
        val title = calendars.unique("Quarterly budget")
        val other = calendars.unique("Gardening")
        calendars.seed(calendars.timed(title, calendars.work, calendars.tomorrowAt(hour = 11)))
        calendars.seed(calendars.timed(other, calendars.home, calendars.tomorrowAt(hour = 12)))

        launchApp()
        clickDescribed("Search")
        waitUntil { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        // The unique part of the title only: the other event must not match it.
        compose.onNode(hasSetTextAction()).performTextInput(title.substringAfterLast(' '))

        waitForRows(title, 1)
        waitForRows(other, 0)
        row(title).performClick()
        waitForText(title)
    }
}
