// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

/**
 * A provider row (or the values to write) as column name to value. Values are `Long`, `Double`,
 * `String` or null, as a cursor reports them. Keeping rows as plain maps keeps the Android
 * classes in one thin gateway and lets the mapping be tested on the JVM.
 */
typealias ProviderRow = Map<String, Any?>

internal fun ProviderRow.long(column: String): Long? = (this[column] as? Number)?.toLong()

internal fun ProviderRow.int(column: String): Int? = (this[column] as? Number)?.toInt()

internal fun ProviderRow.text(column: String): String? = this[column] as? String

/** Whether the column is a non-zero number: provider flags such as `ALL_DAY` and `DELETED`. */
internal fun ProviderRow.flag(column: String): Boolean = (long(column) ?: 0L) != 0L
