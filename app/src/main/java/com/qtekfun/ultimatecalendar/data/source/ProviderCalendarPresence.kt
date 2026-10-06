// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import android.content.ContentResolver
import android.provider.CalendarContract
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.firstrun.CalendarPresence
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Asks the calendar provider whether any calendar exists (RF-01), for the wizard's "add an
 * account" step. A read of the calendars table only; without the permission it counts as none.
 */
class ProviderCalendarPresence @Inject constructor(
    private val resolver: ContentResolver,
    @IoDispatcher private val io: CoroutineDispatcher
) : CalendarPresence {
    override suspend fun hasCalendars(): Boolean = withContext(io) {
        try {
            resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                null,
                null,
                null
            )?.use { it.count > 0 } ?: false
        } catch (_: SecurityException) {
            false
        }
    }
}
