// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.OperationApplicationException
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.provider.CalendarContract
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * [ProviderGateway] over the real `ContentResolver`. It only translates: rows to maps, maps to
 * `ContentValues`, ops to a batch. It never uses `CALLER_IS_SYNCADAPTER`. What it does is checked
 * by the contract suite on an emulator (`ProviderCalendarSourceContractTest`).
 */
@Singleton
class ContentResolverGateway @Inject constructor(@ApplicationContext context: Context) :
    ProviderGateway {
    private val resolver = context.contentResolver

    // Registered as soon as the flow is collected (no dispatch in between), so a change made right
    // after collecting starts is never missed.
    override val changes: Flow<Unit> = flow {
        val signals = Channel<Unit>(Channel.CONFLATED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                signals.trySend(Unit)
            }
        }
        resolver.registerContentObserver(
            "content://${CalendarContract.AUTHORITY}".toUri(),
            true,
            observer
        )
        try {
            for (signal in signals) emit(signal)
        } finally {
            resolver.unregisterContentObserver(observer)
        }
    }

    override fun query(query: ProviderQuery): List<ProviderRow> {
        val args = query.args.takeIf { it.isNotEmpty() }?.toTypedArray()
        val cursor =
            resolver.query(
                queryUri(query),
                query.projection.toTypedArray(),
                query.selection,
                args,
                null
            )
                ?: throw ProviderFailure("calendar provider unavailable")
        return cursor.use { rows(it) }
    }

    override fun apply(ops: List<ProviderOp>): List<Long?> {
        val results = try {
            resolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(ops.map(::operation)))
        } catch (_: OperationApplicationException) {
            throw ProviderFailure("calendar provider refused the changes")
        } catch (_: RemoteException) {
            throw ProviderFailure("calendar provider unavailable")
        }
        return results.map { result -> result.uri?.let { ContentUris.parseId(it) } }
    }

    private fun rows(cursor: Cursor): List<ProviderRow> = buildList {
        while (cursor.moveToNext()) {
            add(
                (0 until cursor.columnCount).associate {
                    cursor.getColumnName(it) to
                        value(cursor, it)
                }
            )
        }
    }

    private fun value(cursor: Cursor, column: Int): Any? = when (cursor.getType(column)) {
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
        Cursor.FIELD_TYPE_STRING -> cursor.getString(column)
        else -> null
    }

    private fun operation(op: ProviderOp): ContentProviderOperation = when (op) {
        is ProviderOp.Insert -> ContentProviderOperation.newInsert(tableUri(op.table))
            .withValues(contentValues(op.values))
            .also { builder ->
                op.parentOp?.let {
                    builder.withValueBackReference(CalendarContract.Attendees.EVENT_ID, it)
                }
            }
            .build()

        is ProviderOp.InsertException ->
            ContentProviderOperation
                .newInsert(
                    ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_EXCEPTION_URI,
                        op.eventId
                    )
                )
                .withValues(contentValues(op.values))
                .build()

        is ProviderOp.Update ->
            ContentProviderOperation
                .newUpdate(ContentUris.withAppendedId(tableUri(op.table), op.id))
                .withValues(contentValues(op.values))
                .build()

        is ProviderOp.Delete -> if (op.id != null) {
            ContentProviderOperation.newDelete(
                ContentUris.withAppendedId(tableUri(op.table), op.id)
            )
        } else {
            ContentProviderOperation.newDelete(tableUri(op.table))
                .withSelection(op.selection, op.args.toTypedArray())
        }.build()
    }

    private fun contentValues(row: ProviderRow) = ContentValues().apply {
        row.forEach { (column, value) ->
            when (value) {
                null -> putNull(column)
                is Long -> put(column, value)
                is Int -> put(column, value)
                is String -> put(column, value)
                else -> throw ProviderFailure("unsupported value type")
            }
        }
    }

    private fun queryUri(query: ProviderQuery): Uri {
        val range = query.rangeMs
        return if (query.table == ProviderTable.INSTANCES && range != null) {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, range.first)
            ContentUris.appendId(builder, range.last)
            builder.build()
        } else {
            tableUri(query.table)
        }
    }

    private fun tableUri(table: ProviderTable): Uri = when (table) {
        ProviderTable.CALENDARS -> CalendarContract.Calendars.CONTENT_URI
        ProviderTable.EVENTS -> CalendarContract.Events.CONTENT_URI
        ProviderTable.ATTENDEES -> CalendarContract.Attendees.CONTENT_URI
        ProviderTable.REMINDERS -> CalendarContract.Reminders.CONTENT_URI
        ProviderTable.INSTANCES -> CalendarContract.Instances.CONTENT_URI
    }
}
