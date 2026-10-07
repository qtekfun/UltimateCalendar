// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Every way to start an invitation check (RF-06) besides the periodic job: the app opening,
 * pull to refresh ([checkNow]) and the source reporting changes while the process lives. Also
 * keeps the periodic job in step with the interval of Settings.
 */
@Singleton
class InvitationCheckCoordinator @Inject constructor(
    private val checker: InvitationChecker,
    private val scheduler: InvitationCheckScheduler,
    private val settings: InvitationCheckSettings
) {
    private val opened = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Schedules the periodic job and watches the source and the app opening until [scope] ends. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        // Replaces the periodic job now and whenever Settings changes the interval.
        scope.launch { settings.intervals.collect { scheduler.apply(it) } }
        // A burst of provider changes (a sync writes many rows) is one check. It does not ask
        // for another sync: that would make a sync cause another sync. Watching begins with a
        // check of its own: the observer is registered a moment after the process starts, and
        // whatever was written before that (an adapter's sync that woke the process, a test
        // seeding right away) produced a change nobody heard. A change that arrives while a check
        // runs is kept (debounce holds the latest) and checked right after it.
        scope.launch {
            checker.sourceChanges
                .onStart { emit(Unit) }
                .debounce(CHANGES_DEBOUNCE_MS)
                .collect { checker.check(false) }
        }
        scope.launch {
            opened.debounce(OPEN_DEBOUNCE_MS).collect { checker.check(true) }
        }
    }

    /** The user opened the app: look for new invitations, asking the accounts to sync first. */
    fun onAppOpened() {
        opened.tryEmit(Unit)
    }

    /** Pull to refresh: checks now, after asking the accounts to sync urgently, and tells how it went. */
    suspend fun checkNow(): InvitationCheckOutcome =
        checker.check(requestSync = true, manual = true)

    private companion object {
        const val CHANGES_DEBOUNCE_MS = 5_000L
        const val OPEN_DEBOUNCE_MS = 1_000L
    }
}
