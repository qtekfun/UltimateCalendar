// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.local.dao.NotifiedInvitationDao
import com.qtekfun.ultimatecalendar.data.local.entity.NotifiedInvitationEntity
import com.qtekfun.ultimatecalendar.data.sync.SourceSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.data.sync.SyncRequests
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationChanges
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationNotifier
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow

/** A clock the test moves by hand. */
class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

class RecordingNotifier : InvitationNotifier {
    val calls = mutableListOf<InvitationChanges>()

    /** Set to make the next calls fail, as a notifier that cannot post would. */
    var failure: Throwable? = null

    override suspend fun notify(changes: InvitationChanges) {
        failure?.let { throw it }
        calls += changes
    }
}

class RecordingSyncRequester : SourceSyncRequester {
    val requests = mutableListOf<Set<CalendarAccount>>()
    val reasons = mutableListOf<SyncReason>()

    override suspend fun requestSync(
        accounts: Set<CalendarAccount>,
        reason: SyncReason
    ): SyncRequests {
        requests += accounts
        reasons += reason
        return SyncRequests(accounts.size, 0)
    }
}

class FixedCheckSettings(
    interval: CheckInterval = CheckInterval.QUARTER_HOUR,
    var aliases: Set<String> = emptySet()
) : InvitationCheckSettings {
    override val intervals = MutableStateFlow(interval)

    var interval: CheckInterval
        get() = intervals.value
        set(value) {
            intervals.value = value
        }

    override suspend fun aliases(): Set<String> = aliases
}

class RecordingScheduler : InvitationCheckScheduler {
    val applied = mutableListOf<CheckInterval>()

    override fun apply(interval: CheckInterval) {
        applied += interval
    }
}

/** The table in memory, for tests that must not wait on a real database thread. */
class InMemoryNotifiedDao : NotifiedInvitationDao() {
    var rows = listOf<NotifiedInvitationEntity>()

    override suspend fun all(): List<NotifiedInvitationEntity> = rows

    override suspend fun clear() {
        rows = emptyList()
    }

    override suspend fun insert(invitations: List<NotifiedInvitationEntity>) {
        rows = invitations
    }
}

object CheckFixtures {
    const val ME = "me@example.com"
    val account = CalendarAccount(ME, "com.google")
    val now: Instant = Instant.parse("2026-06-10T12:00:00Z")

    fun calendar(id: Long = 1, owner: String? = ME) = CalendarInfo(
        id = CalendarId(id),
        account = account,
        displayName = "Calendar $id",
        color = 0,
        access = CalendarAccess.OWNER,
        ownerEmail = owner
    )

    fun invitation(
        title: String = "Lunch",
        start: Instant = now.plusSeconds(3600),
        calendar: Long = 1,
        location: String? = null,
        attendee: String = ME
    ) = EventDraft(
        calendarId = CalendarId(calendar),
        title = title,
        time = EventTime.Timed(start, start.plusSeconds(3600), ZoneOffset.UTC),
        location = location,
        attendees = listOf(Attendee.of(attendee))
    )
}
