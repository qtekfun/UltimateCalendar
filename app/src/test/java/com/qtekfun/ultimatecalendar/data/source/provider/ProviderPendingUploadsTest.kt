// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Events
import com.qtekfun.ultimatecalendar.data.source.CalDavIds
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.EventId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderPendingUploadsTest {
    private val google = CalendarAccount("me@gmail.com", "com.google")

    /** Answers like the provider: the rows of EVENTS that match `dirty=1` or `_id=?`. */
    private class Rows(var events: List<ProviderRow> = emptyList()) : ProviderGateway {
        var failure: RuntimeException? = null
        val queries = mutableListOf<ProviderQuery>()

        override val changes: Flow<Unit> = emptyFlow()

        override fun query(query: ProviderQuery): List<ProviderRow> {
            failure?.let { throw it }
            queries += query
            return when (query.selection) {
                "${Events.DIRTY}=1" -> events.filter { it.flag(Events.DIRTY) }

                "${Events._ID}=?" -> {
                    val id = query.args.single().toLong()
                    events.filter { it[Events._ID] == id }
                }

                else -> error("unexpected selection ${query.selection}")
            }
        }

        override fun apply(ops: List<ProviderOp>): List<Long?> = error("never writes")
    }

    private fun row(id: Long, account: CalendarAccount, dirty: Boolean) = mapOf(
        Events._ID to id,
        Events.ACCOUNT_NAME to account.name,
        Events.ACCOUNT_TYPE to account.type,
        Events.DIRTY to if (dirty) 1L else 0L
    )

    private val gateway = Rows()
    private val uploads = ProviderPendingUploads(gateway, Dispatchers.Unconfined)

    @Test
    fun `the accounts with a change not uploaded are listed, the on-device one never`() = runTest {
        val dav = CalendarAccount("me@cloud.example", "bitfire.at.davdroid")
        gateway.events = listOf(
            row(1, google, dirty = true),
            row(2, google, dirty = true),
            row(3, dav, dirty = false),
            row(4, CalendarAccount("Device", "LOCAL"), dirty = true)
        )
        assertEquals(setOf(google), uploads.accounts())
        gateway.events = emptyList()
        assertEquals(emptySet<CalendarAccount>(), uploads.accounts())
    }

    @Test
    fun `an event is pending while marked, and only the mark is read`() = runTest {
        gateway.events = listOf(row(1, google, dirty = true), row(2, google, dirty = false))
        assertTrue(uploads.isPending(EventId(1)))
        assertFalse(uploads.isPending(EventId(2)))
        assertFalse(uploads.isPending(EventId(404)))
        assertTrue(gateway.queries.all { it.table == ProviderTable.EVENTS })
    }

    @Test
    fun `an on-device event or one of another source is never pending`() = runTest {
        gateway.events = listOf(row(1, CalendarAccount("Device", "LOCAL"), dirty = true))
        assertFalse(uploads.isPending(EventId(1)))
        assertFalse(uploads.isPending(CalDavIds.event(1)))
        assertEquals(1, gateway.queries.size, "another source's event is not looked up")
    }

    @Test
    fun `a provider that refuses the read cannot tell`() = runTest {
        gateway.events = listOf(row(1, google, dirty = true))
        listOf(
            SecurityException("revoked"),
            ProviderFailure("gone"),
            IllegalArgumentException("no such column")
        ).forEach {
            gateway.failure = it
            assertNull(uploads.accounts())
            assertFalse(uploads.isPending(EventId(1)))
        }
    }
}
