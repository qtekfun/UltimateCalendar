// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DavDaoTest {
    private val db = inMemoryDatabase()
    private val accounts = db.davAccountDao()
    private val calendars = db.davCalendarDao()
    private val events = db.davEventDao()

    @AfterEach
    fun close() = db.close()

    private suspend fun account() = accounts.insert(
        DavAccountEntity(serverUrl = "https://cloud.example.com/", loginName = "ana")
    )

    private fun calendar(accountId: Long, href: String, name: String = href, order: Int? = null) =
        DavCalendarEntity(accountId = accountId, href = href, name = name, sortOrder = order)

    private fun event(
        accountId: Long,
        calendarId: Long,
        href: String,
        start: Long,
        end: Long?,
        deleted: Boolean = false
    ) = DavEventEntity(
        accountId = accountId,
        calendarId = calendarId,
        href = href,
        uid = href,
        title = href,
        start = start,
        end = start,
        windowStart = start,
        windowEnd = end,
        deleted = deleted
    )

    @Test
    fun `an account is found by its server and login`() = runTest {
        val id = account()

        assertEquals(id, accounts.find("https://cloud.example.com/", "ana")?.id)
        assertNull(accounts.find("https://cloud.example.com/", "bo"))
        accounts.update(accounts.get(id)!!.copy(calendarHome = "/dav/calendars/ana/"))
        assertEquals("/dav/calendars/ana/", accounts.get(id)?.calendarHome)
    }

    @Test
    fun `upserting a calendar keeps its id, so its events stay`() = runTest {
        val accountId = account()
        val first = calendars.upsert(calendar(accountId, "/cal/work/", "Work"))
        val kept = events.insert(event(accountId, first.id, "/cal/work/1.ics", 1, 2))

        val second = calendars.upsert(calendar(accountId, "/cal/work/", "Job"))

        assertEquals(first.id, second.id)
        assertEquals("Job", calendars.get(first.id)?.name)
        assertEquals(1, calendars.all(accountId).size)
        assertEquals(kept, events.inCalendar(first.id).single().id)
    }

    @Test
    fun `calendars come in server order, those without one last, then by name`() = runTest {
        val accountId = account()
        calendars.upsert(calendar(accountId, "/c/", "charlie"))
        calendars.upsert(calendar(accountId, "/b/", "Bravo"))
        calendars.upsert(calendar(accountId, "/z/", "zulu", order = 2))
        calendars.upsert(calendar(accountId, "/a/", "alpha", order = 1))

        calendars.observeAll(accountId).test {
            assertEquals(listOf("alpha", "zulu", "Bravo", "charlie"), awaitItem().map { it.name })
        }
    }

    @Test
    fun `the sync token and ctag are kept apart from the rest`() = runTest {
        val accountId = account()
        val saved = calendars.upsert(calendar(accountId, "/cal/work/"))

        calendars.setSyncToken(saved.id, "tok-1")
        calendars.setCtag(saved.id, "ctag-1")

        assertEquals("tok-1", calendars.byHref(accountId, "/cal/work/")?.syncToken)
        assertEquals("ctag-1", calendars.get(saved.id)?.ctag)
        calendars.delete(saved.id)
        assertNull(calendars.get(saved.id))
    }

    @Test
    fun `the window query finds the series a range can touch, in order, without deleted ones`() =
        runTest {
            val accountId = account()
            val work = calendars.upsert(calendar(accountId, "/cal/work/")).id
            val home = calendars.upsert(calendar(accountId, "/cal/home/")).id
            events.insert(event(accountId, work, "/w/before", 0, 10))
            events.insert(event(accountId, work, "/w/inside", 20, 30))
            events.insert(event(accountId, work, "/w/repeating", 5, null))
            events.insert(event(accountId, work, "/w/after", 100, 110))
            events.insert(event(accountId, work, "/w/deleted", 20, 30, deleted = true))
            events.insert(event(accountId, home, "/h/inside", 25, 26))

            events.observeWindow(listOf(work), from = 15, to = 50).test {
                assertEquals(listOf("/w/repeating", "/w/inside"), awaitItem().map { it.href })
            }
            events.observeWindow(listOf(work, home), from = 15, to = 50).test {
                assertEquals(
                    listOf("/w/repeating", "/w/inside", "/h/inside"),
                    awaitItem().map { it.href }
                )
            }
            events.observeWindow(listOf(work), from = 10, to = 20).test {
                // Touching bounds do not overlap; the series without an end still can.
                assertEquals(listOf("/w/repeating"), awaitItem().map { it.href })
            }
        }

    @Test
    fun `an event is found by href and removed with its calendar`() = runTest {
        val accountId = account()
        val calendarId = calendars.upsert(calendar(accountId, "/cal/work/")).id
        val id = events.insert(event(accountId, calendarId, "/cal/work/1.ics", 1, 2))

        assertEquals(id, events.byHref(accountId, "/cal/work/1.ics")?.id)
        events.update(events.get(id)!!.copy(title = "Renamed", dirtyFields = 3))
        assertEquals("Renamed", events.get(id)?.title)
        assertNotEquals(0, events.get(id)?.dirtyFields)
        calendars.delete(calendarId)
        assertNull(events.get(id))
    }

    @Test
    fun `an event is deleted by id`() = runTest {
        val accountId = account()
        val calendarId = calendars.upsert(calendar(accountId, "/cal/work/")).id
        val id = events.insert(event(accountId, calendarId, "/cal/work/1.ics", 1, 2))

        events.delete(id)

        assertNull(events.get(id))
    }
}
