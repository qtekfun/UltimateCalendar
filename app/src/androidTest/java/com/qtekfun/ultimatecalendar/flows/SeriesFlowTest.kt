// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.flows

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Flows 4 and 5: changing and deleting repeating events. The series are seeded with a `_SYNC_ID`
 * (see [LocalCalendars]), as the events of Google and DAVx5 have.
 */
@HiltAndroidTest
class SeriesFlowTest : FlowTest() {
    private fun seedDaily(title: String, count: Int) = calendars.seed(
        calendars.timed(
            title,
            calendars.work,
            calendars.tomorrowAt(hour = 10),
            rrule = "FREQ=DAILY;COUNT=$count"
        )
    )

    /** Opens the detail of the [index]th occurrence of [title] from the Agenda. */
    private fun openOccurrence(title: String, count: Int, index: Int) {
        launchApp()
        showAgenda()
        waitForRows(title, count)
        row(title, index).performClick()
        waitForText("One occurrence of a repeating event")
    }

    @Test
    fun editingThisAndFollowingCutsTheSeriesAndStartsANewOne() {
        val title = calendars.unique("Standup")
        val renamed = calendars.unique("Sync")
        val id = seedDaily(title, count = 3)
        val second = calendars.tomorrowAt(hour = 10).plusDays(1).toInstant().toEpochMilli()

        openOccurrence(title, count = 3, index = 1)
        clickDescribed("Edit event")
        waitUntil { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(renamed)
        click("Save")
        click("This and following events")

        waitUntil { calendars.eventsTitled(renamed).isNotEmpty() }
        val events = calendars.events()
        val original = events.single { it.id == id.value }
        val next = events.single { it.title == renamed }
        assertEquals("the old series keeps its title", title, original.title)
        assertTrue(
            "the old series ends: ${original.rrule}",
            original.rrule.orEmpty().contains("UNTIL=")
        )
        assertFalse("COUNT and UNTIL cannot coexist", original.rrule.orEmpty().contains("COUNT="))
        assertEquals("the new series starts at the edited occurrence", second, next.start)
        assertNotNull("the new series repeats", next.rrule)
        assertEquals(
            "one occurrence stays in the old series",
            1,
            calendars.instanceStarts(title).size
        )
        assertEquals("the rest moved to the new one", 2, calendars.instanceStarts(renamed).size)

        showAgenda()
        waitForRows(title, 1)
        waitForRows(renamed, 2)
    }

    @Test
    fun deletingThisAndFollowingCanBeUndone() {
        val title = calendars.unique("Yoga")
        val id = seedDaily(title, count = 3)

        openOccurrence(title, count = 3, index = 1)
        clickDescribed("Delete event")
        click("This and following events")
        click("Delete")

        waitUntil {
            calendars.events().single { it.id == id.value }.rrule.orEmpty().contains("UNTIL=")
        }
        assertEquals("only the first occurrence is left", 1, calendars.instanceStarts(title).size)

        click("Undo")
        waitUntil {
            !calendars.events().single { it.id == id.value }.rrule.orEmpty().contains("UNTIL=")
        }
        assertTrue(
            calendars.events().single {
                it.id == id.value
            }.rrule.orEmpty().contains("COUNT=3")
        )
        assertEquals("the whole series is back", 3, calendars.instanceStarts(title).size)
    }

    /** The app offers no Undo for one occurrence (it cannot put a cancelled one back): it leaves. */
    @Test
    fun deletingOneOccurrenceCancelsOnlyThatOne() {
        val title = calendars.unique("Walk")
        val id = seedDaily(title, count = 3)
        val first = calendars.tomorrowAt(hour = 10).toInstant().toEpochMilli()

        openOccurrence(title, count = 3, index = 1)
        clickDescribed("Delete event")
        // "This event" is the choice a repeating event's dialog starts with.
        click("Delete")

        waitUntil { calendars.instanceStarts(title).size == 2 }
        assertEquals(first, calendars.instanceStarts(title).first())
        assertNull(
            "the series itself is untouched",
            calendars.events().single {
                it.id == id.value
            }.originalId
        )
        waitForRows(title, 2)
    }

    @Test
    fun deletingAnEventCanBeUndone() {
        val title = calendars.unique("Lunch")
        calendars.seed(calendars.timed(title, calendars.home, calendars.tomorrowAt(hour = 13)))

        launchApp()
        showAgenda()
        row(title).performClick()
        clickDescribed("Delete event")
        click("Delete")

        waitUntil { calendars.eventsTitled(title).isEmpty() }
        click("Undo")
        waitUntil { calendars.eventsTitled(title).size == 1 }
        assertEquals(
            "back in its calendar",
            calendars.home.value,
            calendars.eventsTitled(title).single().calendarId
        )
        waitForText(title)
    }
}
