// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.contacts

import com.qtekfun.ultimatecalendar.domain.editor.ContactSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceContactSuggestionsTest {
    private class FakeGateway(var granted: Boolean, var rows: List<ContactRow>) : ContactsGateway {
        val queries = mutableListOf<String>()

        override fun isGranted() = granted

        override fun search(query: String, limit: Int): List<ContactRow> {
            queries += query
            return rows
        }
    }

    private fun suggestions(gateway: FakeGateway) =
        DeviceContactSuggestions(gateway, Dispatchers.Unconfined)

    @Test
    fun `without the permission nothing is read`() = runTest {
        val gateway = FakeGateway(granted = false, rows = listOf(ContactRow("Ana", "ana@x.org")))
        val found = suggestions(gateway)

        assertFalse(found.isAvailable())
        assertEquals(emptyList<ContactSuggestion>(), found.find("an"))
        assertEquals(emptyList<String>(), gateway.queries)
    }

    @Test
    fun `a blank query reads nothing`() = runTest {
        val gateway = FakeGateway(granted = true, rows = listOf(ContactRow("Ana", "ana@x.org")))

        assertTrue(suggestions(gateway).isAvailable())
        assertEquals(emptyList<ContactSuggestion>(), suggestions(gateway).find("   "))
        assertEquals(emptyList<String>(), gateway.queries)
    }

    @Test
    fun `contacts become suggestions with a clean address and name`() = runTest {
        val gateway = FakeGateway(
            granted = true,
            rows = listOf(ContactRow(" Ana López ", " Ana@X.org "), ContactRow("  ", "bob@x.org"))
        )

        val found = suggestions(gateway).find(" an ")

        assertEquals(
            listOf(
                ContactSuggestion("Ana López", "ana@x.org"),
                ContactSuggestion(null, "bob@x.org")
            ),
            found
        )
        assertEquals(listOf("an"), gateway.queries)
    }

    @Test
    fun `rows that are not addresses or repeat one are dropped`() = runTest {
        val gateway = FakeGateway(
            granted = true,
            rows = listOf(
                ContactRow("Ana", "ana@x.org"),
                ContactRow("Ana (work)", "ANA@x.org"),
                ContactRow("No mail", null),
                ContactRow("Broken", "not-an-address")
            )
        )

        assertEquals(
            listOf(ContactSuggestion("Ana", "ana@x.org")),
            suggestions(gateway).find("a")
        )
    }

    @Test
    fun `no more than five are shown`() = runTest {
        val rows = (1..9).map { ContactRow("P$it", "p$it@x.org") }

        val found = suggestions(FakeGateway(granted = true, rows = rows)).find("p")

        assertEquals(DeviceContactSuggestions.MAX, found.size)
        assertEquals("p1@x.org", found.first().email)
    }
}
