// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.conflict

import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import java.time.Instant

/** The two versions of a text field changed on both sides, for the user to choose (rule 1). */
data class TextConflict(val field: EventField, val local: String?, val server: String?)

/** What to do with an event after comparing it with the server. */
sealed interface Resolution {
    /**
     * Keep [event] locally. [push] are the fields where the local value won and still has to
     * reach the server; [conflicts] are text fields waiting for the user, kept with the local
     * text meanwhile and not sent.
     */
    data class Merge(
        val event: IcsEvent,
        val push: Set<EventField>,
        val conflicts: List<TextConflict>
    ) : Resolution

    /** Deleted on the server and unchanged here: delete it locally too. */
    data object DeleteLocally : Resolution

    /** Deleted on the server but changed here: ask to keep a copy or discard it (rule 3). */
    data class DeletedOnServer(val local: IcsEvent) : Resolution
}

/** The local event: its fields, which of them changed here and when the last change was made. */
data class LocalVersion(val event: IcsEvent, val dirty: Set<EventField>, val changedAt: Instant?)

/** The server event, or null when it was deleted, and its LAST-MODIFIED. */
data class ServerVersion(val event: IcsEvent?, val changedAt: Instant?)

/**
 * Three-way merge of an event (SPEC §5). `base` is the version both sides last agreed on (the
 * last one read from the server). Pure: it only decides. The rules, for each field changed here:
 * 1. Changed only here: the local value is kept and sent.
 * 2. Changed on both sides: the last change wins (a tie or an unknown server time keeps the
 *    local one), except text, which is never overwritten: the local text stays, unsent, and the
 *    user chooses.
 * 3. Deleted on the server: unchanged here, deleted here too; changed here, the user chooses.
 *
 * The overrides of a series are merged one occurrence at a time with the same rules, so a
 * cancellation here and an edit of another occurrence on the server both survive.
 */
object ConflictResolver {
    fun resolve(base: IcsEvent?, local: LocalVersion, server: ServerVersion): Resolution {
        val event = server.event
        return when {
            event == null && local.dirty.isEmpty() -> Resolution.DeleteLocally
            event == null -> Resolution.DeletedOnServer(local.event)
            else -> merge(base, local, event, server.changedAt)
        }
    }

    private fun merge(
        base: IcsEvent?,
        local: LocalVersion,
        server: IcsEvent,
        serverChangedAt: Instant?
    ): Resolution.Merge {
        var merged = server
        val push = mutableSetOf<EventField>()
        val conflicts = mutableListOf<TextConflict>()
        val localNewer = localIsNewer(local.changedAt, serverChangedAt)
        for (field in local.dirty) {
            val serverChanged = base == null || field.read(server) != field.read(base)
            val same = field.read(server) == field.read(local.event)
            when {
                same -> Unit

                field == EventField.OVERRIDES -> {
                    val overrides = mergeOverrides(base, local.event, server, localNewer)
                    if (overrides != server.series.overrides.toSet()) {
                        merged = merged.copy(
                            series = merged.series.copy(overrides = overrides.toList())
                        )
                        push += field
                    }
                }

                serverChanged && field.isText -> {
                    merged = field.write(merged, local.event)
                    conflicts += TextConflict(
                        field,
                        field.read(local.event) as String?,
                        field.read(server) as String?
                    )
                }

                !serverChanged || localNewer -> {
                    merged = field.write(merged, local.event)
                    push += field
                }

                else -> Unit
            }
        }
        return Resolution.Merge(merged, push, conflicts)
    }

    /**
     * Each occurrence on its own: where only one side differs from [base] that side wins; where
     * both do (or there is no base), the newer change wins. Server order first, then the
     * occurrences only the local side has.
     */
    private fun mergeOverrides(
        base: IcsEvent?,
        local: IcsEvent,
        server: IcsEvent,
        localNewer: Boolean
    ): Set<OccurrenceOverride> {
        val baseSide = base?.let { it.series.overrides.byKey() }
        val localSide = local.series.overrides.byKey()
        val serverSide = server.series.overrides.byKey()
        return (serverSide.keys + localSide.keys).mapNotNull { key ->
            val l = localSide[key]
            val s = serverSide[key]
            when {
                l == s -> s
                baseSide != null && l == baseSide[key] -> s
                baseSide != null && s == baseSide[key] -> l
                localNewer -> l
                else -> s
            }
        }.toSet()
    }

    private fun List<OccurrenceOverride>.byKey(): Map<OccurrenceKey, OccurrenceOverride> =
        associateBy { it.recurrenceId }

    /** Last change wins (rule 2); a tie or an unknown server time keeps the local change. */
    private fun localIsNewer(local: Instant?, server: Instant?): Boolean =
        server == null || (local != null && !local.isBefore(server))
}
