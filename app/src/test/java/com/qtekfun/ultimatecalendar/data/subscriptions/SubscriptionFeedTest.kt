// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionFeedTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun parse(text: String) = SubscriptionFeed.parse(text, madrid)!!

    @Test
    fun `every event of a feed is read, one series per UID`() {
        val feed = parse(
            FeedIcs.calendar(
                FeedIcs.event("a@x", "First"),
                FeedIcs.event("b@x", "Second", "20261007T100000Z", "20261007T110000Z")
            )
        )

        assertEquals(listOf("First", "Second"), feed.events.map { it.series.event.title })
        assertEquals(listOf("a@x", "b@x"), feed.events.map { it.uid })
        assertEquals(0, feed.skipped)
    }

    @Test
    fun `a master with its changed and cancelled occurrences is one series`() {
        val master = FeedIcs.event(
            "w@x",
            "Weekly",
            extra = "RRULE:FREQ=WEEKLY;COUNT=4\nEXDATE:20261013T100000Z\n"
        )
        val moved = "BEGIN:VEVENT\nUID:w@x\nDTSTAMP:20261001T000000Z\n" +
            "RECURRENCE-ID:20261020T100000Z\nDTSTART:20261020T120000Z\n" +
            "DTEND:20261020T130000Z\nSUMMARY:Weekly (late)\nEND:VEVENT\n"
        val cancelled = "BEGIN:VEVENT\nUID:w@x\nDTSTAMP:20261001T000000Z\n" +
            "RECURRENCE-ID:20261027T100000Z\nDTSTART:20261027T100000Z\nSTATUS:CANCELLED\n" +
            "SUMMARY:Weekly\nEND:VEVENT\n"

        val feed = parse(FeedIcs.calendar(master, moved, cancelled))

        val series = feed.events.single().series
        assertEquals("FREQ=WEEKLY;COUNT=4", series.event.rrule)
        assertEquals(1, series.exDates.size)
        assertEquals(2, series.overrides.size)
        assertEquals("Weekly (late)", series.overrides[0].replacement?.title)
        assertNull(series.overrides[1].replacement)
        assertEquals(0, feed.skipped)
    }

    @Test
    fun `things that are not events are ignored and not counted as lost`() {
        val feed = parse(
            FeedIcs.calendar(FeedIcs.TODO, FeedIcs.event("a@x", "Event"), FeedIcs.JOURNAL)
        )

        assertEquals(listOf("Event"), feed.events.map { it.series.event.title })
        assertEquals(0, feed.skipped)
    }

    @Test
    fun `an event that cannot be read is counted and the rest is kept`() {
        val broken = "BEGIN:VEVENT\nUID:bad@x\nSUMMARY:No time at all\nEND:VEVENT\n"

        val feed = parse(FeedIcs.calendar(broken, FeedIcs.event("a@x", "Fine"), broken))

        assertEquals(listOf("Fine"), feed.events.map { it.series.event.title })
        assertEquals(2, feed.skipped)
    }

    @Test
    fun `garbage lines and a missing end do not stop the reading`() {
        val text = "BEGIN:VCALENDAR\nthis line is not ical\n" + FeedIcs.event("a@x", "Survivor") +
            ":::\nX-WR-CALNAME:Holidays\n"

        assertEquals(listOf("Survivor"), parse(text).events.map { it.series.event.title })
    }

    @Test
    fun `guests and reminders are dropped, so nothing can invite or remind`() {
        val withOverride = FeedIcs.invitation("inv@x") +
            "BEGIN:VEVENT\nUID:inv@x\nDTSTAMP:20261001T000000Z\n" +
            "RECURRENCE-ID:20261006T100000Z\nDTSTART:20261006T120000Z\nDTEND:20261006T130000Z\n" +
            "SUMMARY:Planning (late)\nATTENDEE:mailto:me@example.com\n" +
            "BEGIN:VALARM\nACTION:DISPLAY\nTRIGGER:-PT5M\nDESCRIPTION:x\nEND:VALARM\nEND:VEVENT\n"

        val series = parse(FeedIcs.calendar(withOverride)).events.single().series

        assertEquals(emptyList<Any>(), series.event.attendees)
        assertEquals(emptyList<Any>(), series.event.reminders)
        val replacement = series.overrides.single().replacement!!
        assertEquals("Planning (late)", replacement.title)
        assertEquals(emptyList<Any>(), replacement.attendees)
        assertEquals(emptyList<Any>(), replacement.reminders)
        // What the event says about itself stays.
        assertEquals("boss@example.com", series.event.organizer)
    }

    @Test
    fun `floating times are read in the phone's zone`() {
        val floating = FeedIcs.event("f@x", "Floating", "20261006T100000", "20261006T110000")

        val time = parse(FeedIcs.calendar(floating)).events.single().series.event.time

        assertEquals(
            EventTime.Timed(
                Instant.parse("2026-10-06T08:00:00Z"),
                Instant.parse("2026-10-06T09:00:00Z"),
                madrid
            ),
            time
        )
    }

    @Test
    fun `events without a UID get a stable one of their own`() {
        val text = FeedIcs.calendar(
            FeedIcs.event(null, "Same"),
            FeedIcs.event(null, "Same"),
            FeedIcs.event(null, "Other", "20261008T100000Z", "20261008T110000Z")
        )

        val first = parse(text).events.map { it.uid }
        val again = parse(text).events.map { it.uid }

        assertEquals(3, first.toSet().size)
        assertTrue(first.all { it.startsWith("noid-") })
        assertEquals(first, again)
    }

    @Test
    fun `a UID repeated in a feed keeps its first event and counts the others as left out`() {
        val feed = parse(
            FeedIcs.calendar(
                FeedIcs.event("dup@x", "One"),
                FeedIcs.event("other@x", "Between"),
                FeedIcs.event("dup@x", "Two")
            )
        )

        // The second `VEVENT` with the same UID joins the first one's group and is not a master.
        assertEquals(2, feed.events.size)
        assertEquals(1, feed.skipped)
    }

    @Test
    fun `all-day events keep their dates`() {
        val allDay = FeedIcs.event(
            "d@x",
            "Holiday",
            start = "20261012",
            end = "20261013"
        ).replace("DTSTART:", "DTSTART;VALUE=DATE:").replace("DTEND:", "DTEND;VALUE=DATE:")

        val time = parse(FeedIcs.calendar(allDay)).events.single().series.event.time

        assertTrue(time is EventTime.AllDay)
    }

    @Test
    fun `text that is not a calendar is refused`() {
        assertNull(SubscriptionFeed.parse("<html><body>Please log in</body></html>", madrid))
        assertNull(SubscriptionFeed.parse("", madrid))
        assertNull(SubscriptionFeed.parse("BEGIN:VEVENT\nEND:VEVENT\n", madrid))
    }

    @Test
    fun `a calendar with no events is a valid, empty feed`() {
        val feed = parse(FeedIcs.calendar())

        assertEquals(emptyList<Any>(), feed.events)
        assertEquals(0, feed.skipped)
    }

    @Test
    fun `a feed with many calendars in it is read as one`() {
        val feed = parse(
            FeedIcs.calendar(FeedIcs.event("a@x", "A")) +
                FeedIcs.calendar(FeedIcs.event("b@x", "B"))
        )

        assertEquals(listOf("A", "B"), feed.events.map { it.series.event.title })
    }

    @Test
    fun `a very large feed is read and the events over the cap are counted as left out`() {
        val total = SubscriptionFeed.MAX_EVENTS + 50
        val text = FeedIcs.calendar(
            *Array(total) { FeedIcs.event("e$it@x", "Event $it") }
        )

        val feed = parse(text)

        assertEquals(SubscriptionFeed.MAX_EVENTS, feed.events.size)
        assertEquals(50, feed.skipped)
        assertEquals("Event 0", feed.events.first().series.event.title)
    }
}
