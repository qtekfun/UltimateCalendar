// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.ReRemindEntity
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindEntry
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindKey
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindMoment
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReRemindLogTest {
    private lateinit var database: UltimateCalendarDatabase
    private lateinit var log: ReRemindLog

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
        log = ReRemindLog(database.reRemindDao(), Dispatchers.Unconfined)
    }

    @AfterEach
    fun close() = database.close()

    private fun key(event: Long, moment: ReRemindMoment, start: Long = 1_000) =
        ReRemindKey(InvitationKey(CalendarId(1), EventId(event)), moment, start)

    private fun entry(key: ReRemindKey, at: Long, settled: Boolean) =
        ReRemindEntry(key, Instant.ofEpochMilli(at), settled)

    @Test
    fun `entries come back as stored`() = runTest {
        val rows = listOf(
            entry(key(1, ReRemindMoment.DAY_BEFORE), 10, true),
            entry(key(1, ReRemindMoment.HOUR_BEFORE), 20, false)
        )

        log.apply(rows, emptyList())

        assertEquals(rows.toSet(), log.all().toSet())
    }

    @Test
    fun `the same moment for two accounts of one event is two entries`() = runTest {
        val own = key(1, ReRemindMoment.DAY_BEFORE)
        val foreign = own.copy(invitation = own.invitation.copy(address = "b@gmail.com"))

        log.apply(listOf(entry(own, 10, false), entry(foreign, 20, true)), emptyList())
        assertEquals(setOf(entry(own, 10, false), entry(foreign, 20, true)), log.all().toSet())

        log.apply(emptyList(), listOf(foreign))
        assertEquals(listOf(entry(own, 10, false)), log.all())
    }

    @Test
    fun `an entry is replaced by its key and forgotten keys go in the same write`() = runTest {
        val day = key(1, ReRemindMoment.DAY_BEFORE)
        val hour = key(1, ReRemindMoment.HOUR_BEFORE)
        val moved = key(1, ReRemindMoment.HOUR_BEFORE, start = 5_000)
        log.apply(listOf(entry(day, 10, false), entry(hour, 20, false)), emptyList())

        log.apply(listOf(entry(day, 10, true), entry(moved, 30, false)), listOf(hour))

        assertEquals(
            setOf(entry(day, 10, true), entry(moved, 30, false)),
            log.all().toSet()
        )
    }

    @Test
    fun `a row with a moment this version does not know is left out, not fatal`() = runTest {
        val known = entry(key(1, ReRemindMoment.DAY_BEFORE), 10, false)
        log.apply(listOf(known), emptyList())
        database.reRemindDao().apply(
            listOf(ReRemindEntity(1, 2, "NEXT_WEEK", 1_000, 5, false)),
            emptyList()
        )

        assertEquals(listOf(known), log.all())
    }
}
