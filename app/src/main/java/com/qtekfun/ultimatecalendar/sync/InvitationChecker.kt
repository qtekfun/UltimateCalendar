// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.sync.SourceSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationDetector
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationNotifier
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One check for pending invitations (RF-06, RF-07): asks the source to sync if asked to, finds
 * the invitations in every calendar, compares them with the ones already notified and tells the
 * [InvitationNotifier] what changed.
 *
 * The record of what was notified is replaced only at the very end, in one transaction, after
 * the notifier has been called. A check that is killed or fails halfway records nothing, so the
 * next one starts from the same point and notifies whatever is still pending (RF-08). Checks
 * from different triggers (the periodic job, the app opening, a provider change) run one at a
 * time. Built by `InvitationCheckModule`.
 */
// Every part is a separate port (source, sync, record, notifier, settings, time, threads).
@Suppress("LongParameterList")
class InvitationChecker(
    private val source: CalendarSource,
    private val syncRequester: SourceSyncRequester,
    private val notified: NotifiedInvitations,
    private val notifier: InvitationNotifier,
    private val settings: InvitationCheckSettings,
    private val clock: Clock,
    private val io: CoroutineDispatcher
) {
    private val running = Mutex()
    private val detector = InvitationDetector(clock)

    /** Emits when the source says its data may have changed. */
    val sourceChanges: Flow<Unit> get() = source.changes

    /**
     * Runs a check. [requestSync] first asks every account to sync (the periodic job, the app
     * opening, pull to refresh); a check caused by the provider changing must not, or syncing
     * would trigger itself. Only a [manual] check (the user's pull to refresh) asks urgently and
     * without limit; the others are rate limited (`SyncRequestPolicy`).
     */
    suspend fun check(requestSync: Boolean, manual: Boolean = false): InvitationCheckOutcome =
        running.withLock {
            withContext(io) {
                try {
                    run(requestSync, if (manual) SyncReason.MANUAL else SyncReason.BACKGROUND)
                } catch (_: SecurityException) {
                    // The calendar permission was revoked while the check ran.
                    InvitationCheckOutcome.Failed(CalendarError.PermissionDenied)
                }
            }
        }

    /**
     * The invitations pending right now, soonest first, read without notifying or recording
     * anything: what the tray shows. It does not wait for a running check.
     */
    suspend fun pending(): CalendarResult<List<Invitation>> = withContext(io) {
        try {
            when (val calendars = source.calendars()) {
                is CalendarResult.Failure -> calendars

                is CalendarResult.Success -> {
                    val aliases = settings.aliases()
                    readEvents(null, aliases).map {
                        detector.scan(it, calendars.value, aliases).pending
                    }
                }
            }
        } catch (_: SecurityException) {
            CalendarResult.Failure(CalendarError.PermissionDenied)
        }
    }

    private suspend fun run(requestSync: Boolean, reason: SyncReason): InvitationCheckOutcome =
        when (val calendars = source.calendars()) {
            is CalendarResult.Failure -> InvitationCheckOutcome.Failed(calendars.error)
            is CalendarResult.Success -> runIn(calendars.value, requestSync, reason)
        }

    private suspend fun runIn(
        calendars: List<CalendarInfo>,
        requestSync: Boolean,
        reason: SyncReason
    ): InvitationCheckOutcome {
        // The request is not awaited: whatever the sync brings arrives as a provider change.
        if (requestSync) syncRequester.requestSync(calendars.map { it.account }.toSet(), reason)

        val previous = notified.load()
        val aliases = settings.aliases()
        return when (val events = readEvents(previous.earliestStart(), aliases)) {
            is CalendarResult.Failure -> InvitationCheckOutcome.Failed(events.error)
            is CalendarResult.Success -> finish(previous, events.value, calendars, aliases)
        }
    }

    /** Compares with what was notified, notifies the differences and only then records them. */
    private suspend fun finish(
        previous: List<Invitation>,
        events: List<Event>,
        calendars: List<CalendarInfo>,
        aliases: Set<String>
    ): InvitationCheckOutcome {
        val scan = detector.scan(events, calendars, aliases)
        val changes = detector.diff(previous, scan)
        if (!changes.isEmpty) notifier.notify(changes)
        if (scan.pending.toSet() != previous.toSet()) notified.replaceAll(scan.pending)
        return InvitationCheckOutcome.Done(changes, scan.pending.size)
    }

    /**
     * The events with an occurrence from [from] (or now, if later) to a year ahead. [from] reaches
     * back to the oldest invitation notified, so one whose event has just started is seen as
     * past, not as deleted.
     */
    private suspend fun readEvents(
        from: Instant?,
        aliases: Set<String>
    ): CalendarResult<List<Event>> {
        val now = clock.instant()
        val range = TimeRange(minOf(from ?: now, now), now.plus(HORIZON))
        return when (val instances = source.instances(range)) {
            is CalendarResult.Failure -> instances
            is CalendarResult.Success -> readEach(candidates(instances.value, aliases))
        }
    }

    private suspend fun readEach(ids: List<EventId>): CalendarResult<List<Event>> {
        val events = mutableListOf<Event>()
        for (id in ids) {
            val failure = when (val result = source.event(id)) {
                is CalendarResult.Success -> {
                    events += result.value
                    null
                }

                // Deleted since the instances were read: it is simply not there.
                is CalendarResult.Failure -> result.takeUnless {
                    it.error == CalendarError.NotFound
                }
            }
            if (failure != null) return failure
        }
        return CalendarResult.Success(events)
    }

    /**
     * Only an event where the source says the user is an attendee can be an invitation, so the
     * rest is not read one by one (thousands of reads). The user's own aliases are not known to
     * the source, so with aliases every event that lists attendees is read: one without any cannot
     * be an invitation.
     */
    private fun candidates(instances: List<EventInstance>, aliases: Set<String>) = instances
        .filter { it.selfStatus != null || (aliases.isNotEmpty() && it.hasAttendees) }
        .map { it.eventId }
        .distinct()

    private fun List<Invitation>.earliestStart() = minOfOrNull { it.time.startIn(clock.zone) }

    private companion object {
        val HORIZON: Duration = Duration.ofDays(365)
    }
}
