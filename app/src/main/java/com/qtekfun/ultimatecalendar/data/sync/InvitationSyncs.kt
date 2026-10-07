// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Makes an invitation leave soon (RF-05, RF-06). Google and DAVx5 send an invitation when their
 * sync adapter uploads the event, and the adapter uploads a change the app wrote to the provider
 * only when it next syncs, which may be much later. So after a write that touches an event with
 * guests (or the user's own answer) the app asks that account to sync, expedited
 * ([SyncReason.WRITE]).
 *
 * Writes come in bursts (an event and its occurrences, several answers): the first write of an
 * account starts a short wait of [debounceMs] and everything written meanwhile is covered by the
 * one request that ends it. Only accounts of the Android provider are asked: the on-device
 * account has nothing to sync, the app's own CalDAV account syncs itself a few seconds after a
 * write, and subscriptions have no guests.
 *
 * When a request could not be made (no network, calendar sync switched off, the system refused),
 * [unsent] emits, so the screen can say honestly that the invitations go out when the account
 * syncs by itself.
 */
class InvitationSyncs(
    private val requester: SourceSyncRequester,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEBOUNCE_MS
) {
    private val lock = Any()
    private val waiting = mutableMapOf<CalendarAccount, Job>()
    private val notices = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emits each time a request for an invitation or an answer could not be made. */
    val unsent: SharedFlow<Unit> = notices.asSharedFlow()

    /** An event of [account] with guests, or the user's answer to it, was just written. */
    fun written(account: CalendarAccount) {
        if (account.isLocal || account.isCalDav || account.isSubscription) return
        synchronized(lock) {
            if (waiting[account]?.isActive == true) return
            waiting[account] = scope.launch {
                delay(debounceMs)
                synchronized(lock) { waiting.remove(account) }
                val asked = requester.requestSync(setOf(account), SyncReason.WRITE)
                if (asked.requested == 0) notices.tryEmit(Unit)
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
    }
}
