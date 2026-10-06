// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import com.qtekfun.ultimatecalendar.data.local.dao.CalendarSettingsDao
import com.qtekfun.ultimatecalendar.data.local.dao.PendingCalendarOverrideDao
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.local.entity.PendingCalendarOverrideEntity
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * What [CalendarOverridesBackup.apply] did with the entries it could not apply right away:
 * [missing] found no calendar (or more than one) and were left out; [waiting] belong to the
 * signed-in CalDAV account, whose calendars may not exist until its first sync, and are kept
 * until it creates them.
 */
data class OverridesApplied(val missing: Int = 0, val waiting: Int = 0)

/**
 * Takes the calendars' local name, color and visibility out of this phone for a backup, and puts
 * them back on another one (RF-11). Calendars are matched by account and by the name their
 * source gives them, never by id.
 */
class CalendarOverridesBackup @Inject constructor(
    private val source: CalendarSource,
    private val dao: CalendarSettingsDao,
    private val pending: PendingCalendarOverrideDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /** The overrides of the calendars that still exist; calendars with nothing changed are left out. */
    suspend fun collect(): List<BackupCalendar> = withContext(io) {
        val calendars = source.calendars().getOrNull().orEmpty().associateBy { it.id.value }
        dao.all().mapNotNull { row ->
            val calendar = calendars[row.calendarId]
            val changed = row.displayName != null || row.color != null || row.visible != null
            if (calendar == null || !changed) {
                null
            } else {
                BackupCalendar(
                    accountType = calendar.account.type,
                    accountName = calendar.account.name,
                    name = calendar.displayName,
                    displayName = row.displayName,
                    color = row.color,
                    visible = row.visible
                )
            }
        }
    }

    /**
     * Applies [entries] to the calendars of this phone. Entries that find no calendar (or more
     * than one) to go to are left out rather than guessed, except those of the CalDAV account
     * named [calDavAccount] (the signed-in one, if any): its calendars are only created by the
     * first sync, so those entries are kept and applied then.
     */
    suspend fun apply(
        entries: List<BackupCalendar>,
        calDavAccount: String? = null
    ): OverridesApplied = withContext(io) {
        val calendars = source.calendars().getOrNull().orEmpty()
        val waiting = mutableListOf<PendingCalendarOverrideEntity>()
        var missing = 0
        entries.forEach { entry ->
            val matches = calendars.filter {
                it.account.type == entry.accountType &&
                    it.account.name == entry.accountName &&
                    it.displayName == entry.name
            }
            val match = matches.singleOrNull()
            when {
                match != null -> dao.save(
                    CalendarSettingsEntity(
                        match.id.value,
                        entry.displayName,
                        entry.color,
                        entry.visible
                    )
                )

                matches.isEmpty() && calDavAccount != null &&
                    entry.accountType == CalendarAccount.CALDAV_TYPE &&
                    entry.accountName == calDavAccount ->
                    waiting += PendingCalendarOverrideEntity(
                        entry.accountName,
                        entry.name,
                        entry.displayName,
                        entry.color,
                        entry.visible
                    )

                else -> missing++
            }
        }
        if (waiting.isNotEmpty()) pending.save(waiting)
        OverridesApplied(missing, waiting.size)
    }
}
