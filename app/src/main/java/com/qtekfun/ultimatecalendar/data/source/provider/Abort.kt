// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import com.qtekfun.ultimatecalendar.domain.result.CalendarError

/**
 * Ends a source operation early with [error]. It is thrown and caught inside the provider source
 * only, which turns it into a `CalendarResult.Failure`: nothing leaves the data layer as an
 * exception.
 */
internal class Abort(val error: CalendarError) : RuntimeException()

internal fun abort(error: CalendarError): Nothing = throw Abort(error)
