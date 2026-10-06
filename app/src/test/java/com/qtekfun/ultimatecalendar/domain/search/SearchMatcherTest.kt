// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchMatcherTest {
    private val at = Instant.parse("2026-10-06T10:00:00Z")

    private fun event(
        title: String,
        location: String? = null,
        description: String? = null,
        attendees: List<Attendee> = emptyList()
    ) = SearchableEvent(
        eventId = EventId(1),
        calendarId = CalendarId(1),
        title = title,
        time = EventTime.Timed(at, at.plusSeconds(3_600), ZoneOffset.UTC),
        location = location,
        description = description,
        attendees = attendees
    )

    private fun match(query: String, event: SearchableEvent) =
        SearchMatcher.match(SearchQuery.of(query), event)

    @Test
    fun `an event matches by title, location, description or attendee`() {
        val full = event(
            "Budget review",
            "Harbour cafe",
            "Discuss the roadmap",
            listOf(Attendee.of("zoe@example.com", "Zoe Smith"))
        )

        fun fieldsOf(query: String) = match(query, full)?.fields?.map { it.field }

        assertEquals(listOf(SearchField.TITLE), fieldsOf("budget"))
        assertEquals(listOf(SearchField.LOCATION), fieldsOf("harbour"))
        assertEquals(listOf(SearchField.DESCRIPTION), fieldsOf("roadmap"))
        assertEquals(listOf(SearchField.ATTENDEE), fieldsOf("smith"))
        assertEquals(listOf(SearchField.ATTENDEE), fieldsOf("zoe@example"))
    }

    @Test
    fun `nothing matches when a word is nowhere in the event`() {
        assertNull(match("budget lunch", event("Budget review")))
        assertNull(match("absent", event("Budget review", "Room", "Notes")))
    }

    @Test
    fun `a blank query matches nothing`() {
        assertNull(match("   ", event("Anything")))
        assertNull(SearchMatcher.match(SearchQuery(listOf("")), event("Anything")))
    }

    @Test
    fun `case and accents do not matter in either text`() {
        assertNotNull(match("reunion", event("Reunión semanal")))
        assertNotNull(match("REUNIÓN", event("reunion semanal")))
        val guest = Attendee.of("a@b.c", "Ana Muñoz")
        assertNotNull(match("munoz", event("Cena", attendees = listOf(guest))))
    }

    @Test
    fun `every word must be found, each in any field`() {
        val planning = event("Planning", location = "Room 4")

        assertNotNull(match("room 4 planning", planning))
        assertNull(match("planning room 5", planning))
    }

    @Test
    fun `the highlights are spans of the original text, accents included`() {
        val found = match("reunion", event("La Reunión de hoy"))

        val title = found!!.title
        assertEquals(listOf(3..9), title.highlights)
        assertEquals("Reunión", title.text.substring(3, 10))
    }

    @Test
    fun `touching and overlapping words are one highlight`() {
        assertEquals(listOf(0..3), match("cafe afe", event("Cafe con leche"))!!.title.highlights)
        assertEquals(listOf(0..3), match("ca fe", event("Cafe con leche"))!!.title.highlights)
        assertEquals(
            listOf(0..3, 5..7),
            match("cafe con", event("Cafe con leche"))!!.title.highlights
        )
    }

    @Test
    fun `every occurrence is highlighted, apart`() {
        val found = match("ana", event("Ana y Anabel"))!!

        assertEquals(listOf(0..2, 6..8), found.title.highlights)
    }

    @Test
    fun `an attendee is shown as name and address and found by either`() {
        val ana = Attendee.of("ana@example.com", "Ana Sample")
        val found = match("example", event("Lunch", attendees = listOf(ana)))!!

        val field = found.fields.single()
        assertEquals("Ana Sample · ana@example.com", field.text)
        assertEquals(listOf(17..23), field.highlights)
        assertEquals("ana@example.com", SearchMatcher.label(Attendee.of("ana@example.com")))
        assertEquals("ana@example.com", SearchMatcher.label(Attendee.of("ana@example.com", "  ")))
    }

    @Test
    fun `the title without a match is plain and the detail is the best other field`() {
        val found = match(
            "notes",
            event("Review", "Notes room", "Some notes here", listOf(Attendee.of("n@notes.org")))
        )!!

        assertEquals(emptyList<IntRange>(), found.title.highlights)
        assertEquals("Review", found.title.text)
        assertEquals(
            listOf(SearchField.LOCATION, SearchField.ATTENDEE, SearchField.DESCRIPTION),
            found.fields.map { it.field }
        )
        assertEquals(SearchField.LOCATION, found.detail?.field)
    }

    @Test
    fun `an event matched by its title only has no detail`() {
        assertNull(match("review", event("Review", "Room"))!!.detail)
    }

    @Test
    fun `a title match ranks above a location, attendee and description match`() {
        val scores = listOf(
            match("sam", event("Sam"))!!.score,
            match("sam", event("x", location = "Sam"))!!.score,
            match("sam", event("x", attendees = listOf(Attendee.of("sam@x.org"))))!!.score,
            match("sam", event("x", description = "Sam"))!!.score
        )

        assertEquals(scores.sortedDescending(), scores)
        assertEquals(scores.distinct(), scores)
    }

    @Test
    fun `starting a word scores higher than being inside one`() {
        val starts = match("lun", event("Lunch plan"))!!.score
        val inside = match("lun", event("Blunt plan"))!!.score
        val afterSpace = match("lun", event("Big lunch"))!!.score

        assertTrue(starts > inside)
        assertEquals(starts, afterSpace)
    }

    @Test
    fun `more words found score higher`() {
        val one = match("sam", event("Sam"))!!.score
        val two = match("sam lunch", event("Sam lunch"))!!.score

        assertTrue(two > one)
    }

    @Test
    fun `highlights can be asked of any text`() {
        assertEquals(listOf(2..4), SearchMatcher.highlights("A Café", listOf("caf")))
        assertEquals(listOf(2..5), SearchMatcher.highlights("A Café", listOf("cafe")))
        assertEquals(emptyList<IntRange>(), SearchMatcher.highlights("A Café", listOf("")))
    }
}
