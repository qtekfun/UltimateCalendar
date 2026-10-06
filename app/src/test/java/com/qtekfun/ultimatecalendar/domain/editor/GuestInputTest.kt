// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.form
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class GuestInputTest {
    private fun added(result: GuestInput) = assertInstanceOf(GuestInput.Added::class.java, result)

    private fun invalid(result: GuestInput) =
        assertInstanceOf(GuestInput.Invalid::class.java, result)

    @Test
    fun `an address becomes a guest, trimmed and in lower case`() {
        val result = added(GuestInput.add(form(), "  Ana.Lopez@Example.COM "))

        assertEquals(listOf(Attendee.of("ana.lopez@example.com")), result.form.guests)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `a pasted list adds every address`() {
        val result = added(GuestInput.add(form(), "a@x.org, b@x.org; c@x.org\nd@x.org"))

        assertEquals(
            listOf("a@x.org", "b@x.org", "c@x.org", "d@x.org"),
            result.form.guests.map { it.email }
        )
    }

    @Test
    fun `one bad piece adds nothing and says which one`() {
        val result = invalid(GuestInput.add(form(), "a@x.org, nobody"))

        assertEquals("nobody", result.text)
    }

    @Test
    fun `text without any address is invalid`() {
        assertEquals("   ", invalid(GuestInput.add(form(), "   ")).text)
        assertEquals("a@b", invalid(GuestInput.add(form(), "a@b")).text)
        assertEquals("@x.org", invalid(GuestInput.add(form(), "@x.org")).text)
    }

    @Test
    fun `an address already invited is skipped, whatever its case`() {
        val withGuest = form().copy(attendees = listOf(Attendee.of("a@x.org")))

        val result = added(GuestInput.add(withGuest, "A@X.org, b@x.org, b@x.org"))

        assertEquals(listOf("a@x.org", "b@x.org"), result.form.guests.map { it.email })
        assertEquals(1, result.skipped)
    }

    @Test
    fun `the organizer cannot be invited again`() {
        val owned = form().copy(organizer = "Me@Example.com")

        val result = added(GuestInput.add(owned, "me@example.com"))

        assertEquals(emptyList<Attendee>(), result.form.guests)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `a guest can be removed but the organizer cannot`() {
        val organizer = Attendee.of("me@example.com", isOrganizer = true)
        val guest = Attendee.of("guest@example.com")
        val invited = form().copy(attendees = listOf(organizer, guest))

        assertEquals(listOf(organizer), invited.withoutGuest(guest).attendees)
        assertEquals(invited, invited.withoutGuest(organizer))
    }
}
