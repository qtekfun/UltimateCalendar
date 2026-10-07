// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderPlanner
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * One re-reminder: an invitation, a [moment] and the occurrence it is for. [start] is epoch
 * milliseconds for a timed event and the epoch day for an all-day one, which is a date, not an
 * instant (as in [ReminderPlanner]): it keeps its identity when the phone changes zone, and a
 * moved event gets a new key, so it is reminded again for its new date.
 */
data class ReRemindKey(val invitation: InvitationKey, val moment: ReRemindMoment, val start: Long) {
    /** Text that identifies the key in alarms; ids only, never titles. */
    val tag: String
        get() = "${NotificationTags.id(invitation)}/${moment.name}/$start"

    companion object {
        fun of(invitation: Invitation, moment: ReRemindMoment) = ReRemindKey(
            invitation.key,
            moment,
            when (val time = invitation.time) {
                is EventTime.Timed -> time.start.toEpochMilli()
                is EventTime.AllDay -> time.startDate.toEpochDay()
            }
        )
    }
}

/**
 * What is remembered of a re-reminder: when it is for and whether it is [settled] (it showed, or
 * was already past when first seen, so it must never show). Kept in Room.
 */
data class ReRemindEntry(val key: ReRemindKey, val at: Instant, val settled: Boolean)

/** What the planner needs to know besides the invitations and the log. */
data class ReRemindContext(
    val option: ReRemindOption,
    val allDayTime: LocalTime,
    val zone: ZoneId,
    val now: Instant,
    /** How far back a re-reminder that did not show is still brought back (RF-08). */
    val window: Duration
)

/** What to do after a look at the invitations (see [ReRemindPlanner.plan]). */
data class ReRemindPlan(
    /** Rows to write: new ones and changed ones. */
    val save: List<ReRemindEntry>,
    /** Rows to forget: answered, cancelled, moved, past or switched off. */
    val delete: List<ReRemindKey>,
    /** The alarms that must exist, soonest first; every other alarm is cancelled. */
    val alarms: List<ReRemindEntry>,
    /** Invitations to remind about now, at most once each; already settled in [save]. */
    val due: List<Invitation>
)

/**
 * Decides, as a pure function, which re-reminders exist, which ring and which are forgotten
 * (T40). Idempotent: planning again with its own [ReRemindPlan.save] applied gives no more
 * changes and nothing due. A moment already past when first seen is settled without showing, so
 * switching the option on, a new invitation or the first run never floods; one that was planned
 * earlier and went by while the app was dead shows once, within the window (RF-08).
 */
object ReRemindPlanner {
    /** Alarms kept at once; with [ReminderPlanner.MAX_ALARMS] far under Android's 500. */
    const val MAX_ALARMS = 50

    /** An alarm that rings late (Doze) is still on time; also what a window of 0 allows. */
    val ON_TIME_GRACE: Duration = Duration.ofMinutes(GRACE_MINUTES)

    private const val GRACE_MINUTES = 5L

    fun plan(
        pending: List<Invitation>,
        stored: Collection<ReRemindEntry>,
        context: ReRemindContext
    ): ReRemindPlan {
        val known = stored.associateBy { it.key }
        val candidates = pending
            .filter { it.time.startIn(context.zone).isAfter(context.now) }
            .flatMap { invitation ->
                context.option.moments.map { Candidate(invitation, ReRemindKey.of(invitation, it)) }
            }
            .distinctBy { it.key }
        val save = mutableListOf<ReRemindEntry>()
        val live = mutableListOf<ReRemindEntry>()
        val due = mutableListOf<Invitation>()
        for (candidate in candidates) {
            val old = known[candidate.key]
            val resolved = resolve(candidate, old, context)
            if (resolved.show) due += candidate.invitation
            if (resolved.entry != old) save += resolved.entry
            if (!resolved.entry.settled) live += resolved.entry
        }
        val wanted = candidates.mapTo(mutableSetOf()) { it.key }
        val horizon = context.now.plus(ReminderPlanner.HORIZON)
        return ReRemindPlan(
            save = save,
            delete = known.keys.filter { it !in wanted },
            alarms = live.filter { it.at <= horizon }
                .sortedWith(compareBy<ReRemindEntry> { it.at }.thenBy { it.key.tag })
                .take(MAX_ALARMS),
            due = due.distinctBy { it.key }.sortedBy { it.time.startIn(context.zone) }
        )
    }

    private class Candidate(val invitation: Invitation, val key: ReRemindKey)

    private class Resolved(val entry: ReRemindEntry, val show: Boolean)

    private fun resolve(
        candidate: Candidate,
        old: ReRemindEntry?,
        context: ReRemindContext
    ): Resolved {
        val time = candidate.invitation.time
        val at = candidate.key.moment.fireAt(time, context.allDayTime, context.zone)
        val base = when {
            // Past when first seen (a new invitation, the option just switched on): never shows.
            old == null -> ReRemindEntry(candidate.key, at, settled = !at.isAfter(context.now))

            old.settled -> old

            else -> old.copy(at = at)
        }
        // Planned earlier and went by since: the alarm rang, or the app was dead.
        val overdue = !base.settled && !base.at.isAfter(context.now)
        val recent = base.at.isAfter(context.now.minus(maxOf(context.window, ON_TIME_GRACE)))
        return Resolved(if (overdue) base.copy(settled = true) else base, overdue && recent)
    }
}
