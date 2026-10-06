// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttendeeGroupingTest {
    private fun event(organizer: String?, vararg attendees: Attendee) = Event(
        EventId(1),
        CalendarId(1),
        "Meeting",
        EventTime.AllDay(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-07")),
        organizer = organizer,
        attendees = attendees.toList()
    )

    private fun person(email: String, status: AttendeeStatus, name: String? = null) =
        Attendee.of(email, name, status = status)

    @Test
    fun `no attendees, no block`() {
        assertNull(AttendeeGrouping.group(event("me@x.org"), listOf("me@x.org")))
    }

    @Test
    fun `groups come as accepted, maybe, declined and no answer, without empty ones`() {
        val groups = AttendeeGrouping.group(
            event(
                null,
                person("a@x.org", AttendeeStatus.NEEDS_ACTION),
                person("b@x.org", AttendeeStatus.DECLINED),
                person("c@x.org", AttendeeStatus.ACCEPTED),
                person("d@x.org", AttendeeStatus.TENTATIVE)
            ),
            emptyList()
        )
        assertEquals(
            listOf(
                AttendeeStatus.ACCEPTED,
                AttendeeStatus.TENTATIVE,
                AttendeeStatus.DECLINED,
                AttendeeStatus.NEEDS_ACTION
            ),
            groups?.groups?.map { it.status }
        )
        assertEquals(4, groups?.total)
    }

    @Test
    fun `an empty group is left out`() {
        val groups = AttendeeGrouping.group(
            event(null, person("c@x.org", AttendeeStatus.ACCEPTED)),
            emptyList()
        )
        assertEquals(listOf(AttendeeStatus.ACCEPTED), groups?.groups?.map { it.status })
    }

    @Test
    fun `the user comes first in a group and the others by name`() {
        val groups = AttendeeGrouping.group(
            event(
                null,
                person("z@x.org", AttendeeStatus.ACCEPTED, "Zoe"),
                person("me@x.org", AttendeeStatus.ACCEPTED, "Mike"),
                person("a@x.org", AttendeeStatus.ACCEPTED, "alice"),
                person("b@x.org", AttendeeStatus.ACCEPTED)
            ),
            listOf("ME@x.org")
        )
        val rows = groups?.groups?.single()?.rows.orEmpty()
        assertEquals(listOf("Mike", "alice", "b@x.org", "Zoe"), rows.map { it.label })
        assertEquals(listOf(true, false, false, false), rows.map { it.isSelf })
    }

    @Test
    fun `a blank name falls back to the address`() {
        assertEquals(
            "a@x.org",
            AttendeeRow(person("a@x.org", AttendeeStatus.ACCEPTED, " "), false).label
        )
    }

    @Test
    fun `the flagged organizer is first and not repeated in the groups`() {
        val groups = AttendeeGrouping.group(
            event(
                "boss@x.org",
                person("a@x.org", AttendeeStatus.ACCEPTED),
                Attendee.of(
                    "boss@x.org",
                    "Boss",
                    status = AttendeeStatus.ACCEPTED,
                    isOrganizer = true
                )
            ),
            emptyList()
        )
        assertEquals("Boss", groups?.organizer?.label)
        assertEquals(listOf("a@x.org"), groups?.groups?.single()?.rows?.map { it.label })
        assertEquals(2, groups?.total)
    }

    @Test
    fun `the organizer is found by the event's organizer address`() {
        val groups = AttendeeGrouping.group(
            event(
                " Boss@X.org ",
                person("a@x.org", AttendeeStatus.ACCEPTED),
                person("boss@x.org", AttendeeStatus.TENTATIVE, "Boss")
            ),
            emptyList()
        )
        assertEquals("Boss", groups?.organizer?.label)
        assertEquals(AttendeeStatus.TENTATIVE, groups?.organizer?.attendee?.status)
        assertEquals(1, groups?.groups?.size)
    }

    @Test
    fun `an organizer that is not listed is added as accepted`() {
        val groups = AttendeeGrouping.group(
            event("boss@x.org", person("a@x.org", AttendeeStatus.NEEDS_ACTION)),
            emptyList()
        )
        val organizer = groups?.organizer
        assertNotNull(organizer)
        assertEquals("boss@x.org", organizer?.label)
        assertEquals(AttendeeStatus.ACCEPTED, organizer?.attendee?.status)
        assertTrue(organizer?.attendee?.isOrganizer == true)
    }

    @Test
    fun `without any organizer nobody is first`() {
        val groups = AttendeeGrouping.group(
            event(null, person("a@x.org", AttendeeStatus.ACCEPTED)),
            emptyList()
        )
        assertNull(groups?.organizer)
        assertEquals(1, groups?.total)
    }

    @Test
    fun `the user is marked when organizer too`() {
        val groups = AttendeeGrouping.group(
            event("me@x.org", person("a@x.org", AttendeeStatus.ACCEPTED)),
            listOf("me@x.org")
        )
        assertTrue(groups?.organizer?.isSelf == true)
        assertFalse(groups?.groups?.single()?.rows?.single()?.isSelf == true)
    }
}
