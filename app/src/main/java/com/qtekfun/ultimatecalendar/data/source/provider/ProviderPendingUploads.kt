// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Events
import com.qtekfun.ultimatecalendar.data.source.SourceKind
import com.qtekfun.ultimatecalendar.data.sync.PendingUploads
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.EventId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [PendingUploads] from the provider's own mark: it sets an event's `DIRTY` when a normal client
 * changes it or its attendees, and the sync adapter clears it once the change is uploaded. The
 * column is only read, never written. The on-device account is left out: nothing ever uploads
 * it, so its events stay marked for good.
 */
class ProviderPendingUploads @Inject constructor(
    private val gateway: ProviderGateway,
    @IoDispatcher private val io: CoroutineDispatcher
) : PendingUploads {
    override suspend fun accounts(): Set<CalendarAccount>? = read {
        gateway.query(
            ProviderQuery(ProviderTable.EVENTS, ACCOUNT_COLUMNS, "${Events.DIRTY}=1")
        ).mapNotNull(::accountOf).filterNot { it.isLocal }.toSet()
    }

    override suspend fun isPending(id: EventId): Boolean {
        if (SourceKind.of(id) != SourceKind.PROVIDER) return false
        return read {
            gateway.query(
                ProviderQuery(
                    ProviderTable.EVENTS,
                    ACCOUNT_COLUMNS + Events.DIRTY,
                    "${Events._ID}=?",
                    listOf(id.value.toString())
                )
            ).any { it.flag(Events.DIRTY) && accountOf(it)?.isLocal == false }
        } == true
    }

    private fun accountOf(row: ProviderRow): CalendarAccount? {
        val name = row.text(Events.ACCOUNT_NAME)
        val type = row.text(Events.ACCOUNT_TYPE)
        return if (name != null && type != null) CalendarAccount(name, type) else null
    }

    /** Runs [block] off the main thread; a refused or missing provider answers null. */
    private suspend fun <T> read(block: () -> T): T? = withContext(io) {
        try {
            block()
        } catch (_: SecurityException) {
            null
        } catch (_: ProviderFailure) {
            null
        } catch (_: IllegalArgumentException) {
            // A provider that does not offer the column to normal clients.
            null
        }
    }

    private companion object {
        val ACCOUNT_COLUMNS = listOf(Events.ACCOUNT_NAME, Events.ACCOUNT_TYPE)
    }
}
