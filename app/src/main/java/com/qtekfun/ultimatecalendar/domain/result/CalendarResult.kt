// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.result

/** Why an operation on a calendar source failed. Never carries titles or addresses. */
sealed interface CalendarError {
    /** The calendar permission was not granted (or was revoked). */
    data object PermissionDenied : CalendarError

    data object NotFound : CalendarError

    /** The calendar's access level does not allow the change. */
    data object ReadOnly : CalendarError

    /** The source rejected the data; [reason] is a short technical note for logs. */
    data class Invalid(val reason: String) : CalendarError

    /** The source failed or is not available (provider gone, network down). */
    data class SourceFailure(val reason: String) : CalendarError
}

/** The outcome of an operation on a calendar source: a value or a [CalendarError]. */
sealed interface CalendarResult<out T> {
    data class Success<T>(val value: T) : CalendarResult<T>

    data class Failure(val error: CalendarError) : CalendarResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value

    fun <R> map(transform: (T) -> R): CalendarResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> CalendarResult<R>): CalendarResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }
}
