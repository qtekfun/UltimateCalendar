// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.subscriptions.AddResult
import com.qtekfun.ultimatecalendar.data.subscriptions.RefreshResult
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionRefresher
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionRepository
import com.qtekfun.ultimatecalendar.domain.subscriptions.RefreshInterval
import com.qtekfun.ultimatecalendar.domain.subscriptions.Subscription
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A subscription of the list, and whether it is being downloaded right now. */
data class SubscriptionRow(val subscription: Subscription, val refreshing: Boolean)

/** What the user is told after a refresh they asked for; the screen words it. */
sealed interface SubscriptionNote {
    data class Refreshed(val events: Int, val skipped: Int) : SubscriptionNote

    data object Unchanged : SubscriptionNote

    data class Failed(val result: RefreshResult.Failed) : SubscriptionNote
}

/**
 * Settings → Calendar subscriptions (T39): the list with each one's state, and the actions. The
 * rules (address checks, names, scheduling) are the repository's; this only joins the list with
 * what is being refreshed and turns the outcome of a refresh into a note.
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val refresher: SubscriptionRefresher
) : ViewModel() {
    val rows: StateFlow<List<SubscriptionRow>> =
        combine(repository.subscriptions, refresher.refreshing) { all, busy ->
            all.map { SubscriptionRow(it, it.id in busy) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

    private val mutableNotes = MutableSharedFlow<SubscriptionNote>(extraBufferCapacity = NOTES)

    /** What to tell the user about the refreshes they asked for. */
    val notes: SharedFlow<SubscriptionNote> = mutableNotes.asSharedFlow()

    /** Subscribes and starts downloading; the result says whether the address was accepted. */
    suspend fun add(
        address: String,
        name: String,
        color: Int,
        interval: RefreshInterval
    ): AddResult = repository.add(address, name, color, interval).also {
        if (it is AddResult.Added) refresh(it.id)
    }

    fun refresh(id: Long): Job = viewModelScope.launch { refreshAndTell(id) }

    fun refreshAll(): Job = viewModelScope.launch { refresher.refreshAll() }

    fun edit(id: Long, name: String, color: Int): Job =
        viewModelScope.launch { repository.rename(id, name, color) }

    /** Turning one on refreshes it at once (an unchanged feed costs one small request). */
    fun setEnabled(id: Long, enabled: Boolean): Job = viewModelScope.launch {
        repository.setEnabled(id, enabled)
        if (enabled) refreshAndTell(id)
    }

    fun setInterval(id: Long, interval: RefreshInterval): Job =
        viewModelScope.launch { repository.setInterval(id, interval) }

    fun remove(id: Long): Job = viewModelScope.launch { repository.remove(id) }

    private suspend fun refreshAndTell(id: Long) {
        when (val result = refresher.refresh(id)) {
            is RefreshResult.Updated ->
                mutableNotes.tryEmit(SubscriptionNote.Refreshed(result.events, result.skipped))

            RefreshResult.Unchanged -> mutableNotes.tryEmit(SubscriptionNote.Unchanged)

            is RefreshResult.Failed -> mutableNotes.tryEmit(SubscriptionNote.Failed(result))

            RefreshResult.Gone -> Unit
        }
    }

    private companion object {
        const val STOP_MS = 5_000L
        const val NOTES = 4
    }
}
