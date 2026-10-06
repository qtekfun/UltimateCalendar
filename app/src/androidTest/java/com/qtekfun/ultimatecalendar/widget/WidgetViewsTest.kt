// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaEntry
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaSlot
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.widget.AgendaWidgetRow
import com.qtekfun.ultimatecalendar.domain.widget.MonthWidgets
import com.qtekfun.ultimatecalendar.domain.widget.WidgetPalette
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widgets' RemoteViews with made-up data: they inflate, every call they make is one a
 * RemoteViews may make (a wrong method name only fails when applied), and the texts land where
 * they should. Nothing is read from the calendars of the device, and no widget is placed.
 */
class WidgetViewsTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.parse("2026-10-06")
    private val formats = WidgetFormats(context, zone)
    private val light = WidgetPalette.of(dark = false, amoled = false, tones = null)
    private val amoled = WidgetPalette.of(dark = true, amoled = true, tones = null)

    private fun apply(views: RemoteViews): View = views.apply(context, FrameLayout(context))

    private fun View.texts(): List<String> {
        val found = mutableListOf<String>()
        fun walk(view: View) {
            if (view is TextView && view.visibility == View.VISIBLE) found += view.text.toString()
            if (view is ViewGroup) (0 until view.childCount).forEach { walk(view.getChildAt(it)) }
        }
        walk(this)
        return found
    }

    private fun instance(title: String, status: AttendeeStatus? = null) = EventInstance(
        EventId(1),
        CalendarId(1),
        title,
        EventTime.Timed(
            Instant.parse("2026-10-06T14:00:00Z"),
            Instant.parse("2026-10-06T15:00:00Z"),
            zone
        ),
        location = "Room 4",
        color = 0xFF2E7D32.toInt(),
        selfStatus = status
    )

    @Test
    fun theMonthWidgetInflatesWithItsGrid() {
        val instances = listOf(instance("Standup"), instance("Review", AttendeeStatus.NEEDS_ACTION))
        val model = MonthWidgets.build(
            YearMonth.of(2026, 10),
            today,
            DayOfWeek.MONDAY,
            zone,
            instances,
            emptyMap(),
            maxMarkers = 1
        )

        listOf(light, amoled).forEach { palette ->
            val view = apply(MonthWidgetViews.grid(context, 7, model, palette, formats))
            val texts = view.texts()

            assertTrue(texts.contains("6"))
            assertTrue(texts.any { it.startsWith("+") })
            assertNotNull(view.findViewById<View>(R.id.cell_0_0))
        }
    }

    @Test
    fun aSixWeekMonthShowsAllSixRowsAndAFiveWeekOneHidesTheLast() {
        fun lastRowVisibility(month: YearMonth): Int {
            val model = MonthWidgets.build(
                month,
                today,
                DayOfWeek.MONDAY,
                zone,
                emptyList(),
                emptyMap(),
                maxMarkers = 3
            )
            return apply(MonthWidgetViews.grid(context, 7, model, light, formats))
                .findViewById<View>(R.id.week_row_5).visibility
        }

        assertEquals(View.VISIBLE, lastRowVisibility(YearMonth.of(2026, 8)))
        assertEquals(View.GONE, lastRowVisibility(YearMonth.of(2026, 10)))
    }

    @Test
    fun theAgendaRowsInflateWithTheirTexts() {
        val entry = AgendaEntry(
            instance("Standup", AttendeeStatus.NEEDS_ACTION),
            AgendaSlot.Span(
                Instant.parse("2026-10-06T14:00:00Z"),
                Instant.parse("2026-10-06T15:00:00Z")
            ),
            0xFF2E7D32.toInt(),
            null
        )

        val event =
            apply(AgendaWidgetViews.event(context, AgendaWidgetRow.Event(entry), light, formats))
        val day = apply(
            AgendaWidgetViews.day(
                context,
                AgendaWidgetRow.Day(today, isToday = true, isTomorrow = false),
                light,
                formats
            )
        )
        val message = apply(AgendaWidgetViews.message(context, "Nothing", amoled))

        assertTrue(event.texts().contains("Standup"))
        assertTrue(event.texts().any { it.contains("Room 4") })
        assertEquals(View.VISIBLE, event.findViewById<View>(R.id.event_marker_pending).visibility)
        assertEquals(View.GONE, event.findViewById<View>(R.id.event_marker).visibility)
        assertTrue(
            event.findViewById<View>(
                R.id.event_root
            ).contentDescription.toString().contains("Standup")
        )
        assertTrue(day.texts().single().contains("6"))
        assertEquals(listOf("Nothing"), message.texts())
    }

    @Test
    fun aMessageWidgetInflates() {
        val view = apply(MonthWidgetViews.message(context, "No access", light))

        assertEquals(listOf("No access"), view.texts())
    }
}
