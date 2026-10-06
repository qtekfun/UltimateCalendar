// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import com.qtekfun.ultimatecalendar.data.local.dao.CalendarSettingsDao
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Takes the calendars' local name, color and visibility out of this phone for a backup, and puts
 * them back on another one (RF-11). Calendars are matched by account and by the name their
 * source gives them, never by id.
 */
class CalendarOverridesBackup @Inject constructor(
    private val source: CalendarSource,
    private val dao: CalendarSettingsDao,
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
     * Applies [entries] to the calendars of this phone and returns how many found no calendar
     * (or more than one) to go to; those are left out rather than guessed.
     */
    suspend fun apply(entries: List<BackupCalendar>): Int = withContext(io) {
        val calendars = source.calendars().getOrNull().orEmpty()
        entries.count { entry ->
            val match = calendars.singleOrNull {
                it.account.type == entry.accountType &&
                    it.account.name == entry.accountName &&
                    it.displayName == entry.name
            }
            if (match != null) {
                dao.save(
                    CalendarSettingsEntity(
                        match.id.value,
                        entry.displayName,
                        entry.color,
                        entry.visible
                    )
                )
            }
            match == null
        }
    }
}
