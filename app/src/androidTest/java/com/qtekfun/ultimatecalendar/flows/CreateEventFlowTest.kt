// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.flows

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Flow 1: create an event from the Create button and see it in the Agenda. */
@HiltAndroidTest
class CreateEventFlowTest : FlowTest() {
    @Test
    fun createdEventIsStoredWithItsTimeCalendarAndReminderAndShownInTheAgenda() {
        val title = calendars.unique("Dentist")
        launchApp()

        clickDescribed("Create")
        waitUntil { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(title)
        setStartTime(hour = 10, minute = 15)
        chooseCalendar("UI Home")
        // A new event already has the default 10-minute reminder; add a second one.
        clickDescribed("Add a reminder", scroll = true)
        click("1 hour before")
        click("Save")

        waitUntil { calendars.eventsTitled(title).isNotEmpty() }
        val stored = calendars.eventsTitled(title).single()
        assertEquals("calendar", calendars.home.value, stored.calendarId)
        val start = Instant.ofEpochMilli(stored.start).atZone(calendars.zone)
        assertEquals("start hour", 10, start.hour)
        assertEquals("start minute", 15, start.minute)
        assertEquals("one hour long", HOUR_MS, checkNotNull(stored.end) - stored.start)
        assertEquals("reminders", listOf(10, 60), calendars.reminderMinutes(stored.id))

        showAgenda()
        waitForRows(title, 1)
    }

    /**
     * Opens the start time and taps [hour] and then [minute] on the dial, which switches itself to
     * minutes after the hour. The dial's numbers describe themselves ("10 o'clock", "15 minutes")
     * and a tap lands on the number it is made on, as a finger would.
     */
    private fun setStartTime(hour: Int, minute: Int) {
        clickDescribed("Start time", substring = true)
        waitForText("Select time")
        clickDial("$hour o'clock")
        clickDial("$minute minutes")
        // On a 12-hour clock the hour needs its half of the day; 24-hour clocks have none.
        if (compose.onAllNodesWithText("AM").fetchSemanticsNodes().isNotEmpty()) click("AM")
        click("OK")
    }

    private fun clickDial(description: String) {
        val number = hasContentDescription(description) and hasAnyAncestor(isDialog())
        waitUntil { compose.onAllNodes(number).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(number).onFirst().performClick()
    }

    private fun chooseCalendar(name: String) {
        clickDescribed("Calendar: ", substring = true, scroll = true)
        clickDescribed(name)
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
