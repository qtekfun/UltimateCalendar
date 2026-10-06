// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.InvitationChecker
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform

/**
 * The invitations waiting for an answer, live (RF-06): what the tray lists and what its badge
 * counts. It reads every calendar, visible or hidden, through the [InvitationChecker], and reads
 * again when the source changes or [refresh] is called. A read that fails keeps the last list.
 */
@Singleton
class InvitationInbox @Inject constructor(private val checker: InvitationChecker) {
    private val refreshes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Soonest first. Emits once at the start and again whenever the answer may have changed. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun pending(): Flow<List<Invitation>> = merge(
        flowOf(Unit),
        refreshes,
        // A sync writes many rows: a burst of changes is one read.
        checker.sourceChanges.debounce(CHANGES_DEBOUNCE_MS)
    ).mapLatest { checker.pending() }
        .transform { if (it is CalendarResult.Success) emit(it.value) }

    /** Reads again now, for the open tray (after a pull to refresh). */
    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    private companion object {
        const val CHANGES_DEBOUNCE_MS = 1_000L
    }
}
