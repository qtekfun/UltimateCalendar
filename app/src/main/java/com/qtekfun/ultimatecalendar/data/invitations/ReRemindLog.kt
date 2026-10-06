// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.local.dao.ReRemindDao
import com.qtekfun.ultimatecalendar.data.local.entity.ReRemindEntity
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindEntry
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindKey
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindMoment
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The re-reminders of unanswered invitations (T40) in Room, so that one that showed is never
 * shown again, even after the process was killed or the phone restarted.
 */
@Singleton
class ReRemindLog @Inject constructor(
    private val dao: ReRemindDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /** Every entry; one whose moment this app version does not know is left out. */
    suspend fun all(): List<ReRemindEntry> = withContext(io) {
        dao.all().mapNotNull { row ->
            ReRemindMoment.entries.firstOrNull { it.name == row.moment }?.let { moment ->
                val invitation = InvitationKey(CalendarId(row.calendarId), EventId(row.eventId))
                ReRemindEntry(
                    ReRemindKey(invitation, moment, row.start),
                    Instant.ofEpochMilli(row.at),
                    row.settled
                )
            }
        }
    }

    /** Writes [save] and forgets [delete], all or nothing. */
    suspend fun apply(save: List<ReRemindEntry>, delete: List<ReRemindKey>) = withContext(io) {
        dao.apply(save.map { it.toRow() }, delete.map { it.toRow(at = 0, settled = false) })
    }

    private fun ReRemindEntry.toRow() = key.toRow(at.toEpochMilli(), settled)

    private fun ReRemindKey.toRow(at: Long, settled: Boolean) = ReRemindEntity(
        calendarId = invitation.calendarId.value,
        eventId = invitation.eventId.value,
        moment = moment.name,
        start = start,
        at = at,
        settled = settled
    )
}
