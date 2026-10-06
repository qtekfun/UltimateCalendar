// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import kotlinx.coroutines.flow.Flow

/** The tables of the calendar provider the app reads and writes. */
enum class ProviderTable { CALENDARS, EVENTS, ATTENDEES, REMINDERS, INSTANCES }

/**
 * A read. [rangeMs] (inclusive epoch milliseconds, as `Instances` takes it) is required for
 * [ProviderTable.INSTANCES] and ignored for the other tables.
 */
data class ProviderQuery(
    val table: ProviderTable,
    val projection: List<String>,
    val selection: String? = null,
    val args: List<String> = emptyList(),
    val rangeMs: LongRange? = null
)

/** One write of an atomic batch. Always done as a normal client, never as a sync adapter. */
sealed interface ProviderOp {
    /**
     * Inserts a row. When [parentOp] is the index of an earlier insert of the batch, the new row's
     * `EVENT_ID` is that insert's id.
     */
    data class Insert(
        val table: ProviderTable,
        val values: ProviderRow,
        val parentOp: Int? = null
    ) : ProviderOp

    /** Creates an exception of the series [eventId] (`CONTENT_EXCEPTION_URI`). */
    data class InsertException(val eventId: Long, val values: ProviderRow) : ProviderOp

    data class Update(val table: ProviderTable, val id: Long, val values: ProviderRow) :
        ProviderOp

    /** Deletes the row [id], or the rows matching [selection] when [id] is null. */
    data class Delete(
        val table: ProviderTable,
        val id: Long? = null,
        val selection: String? = null,
        val args: List<String> = emptyList()
    ) : ProviderOp
}

/** The provider is missing or refused a batch; [reason] never carries titles or addresses. */
class ProviderFailure(val reason: String) : RuntimeException(reason)

/**
 * The only door to the Android calendar provider (`ContentResolver`). Calls block and must run
 * off the main thread. A missing permission surfaces as [SecurityException], any other provider
 * trouble as [ProviderFailure].
 */
interface ProviderGateway {
    /** Emits whenever the provider says its data changed. */
    val changes: Flow<Unit>

    fun query(query: ProviderQuery): List<ProviderRow>

    /** Applies [ops] in one batch and returns the id of each insert (null for other ops). */
    fun apply(ops: List<ProviderOp>): List<Long?>
}
