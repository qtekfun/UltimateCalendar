// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.invitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.invitations.InvitationInbox
import com.qtekfun.ultimatecalendar.data.invitations.InvitationResponses
import com.qtekfun.ultimatecalendar.data.invitations.ResponseOutcome
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAnswer
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationDay
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationDays
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.notify.InvitationNotificationSurface
import com.qtekfun.ultimatecalendar.notify.SystemZone
import com.qtekfun.ultimatecalendar.sync.InvitationCheckCoordinator
import com.qtekfun.ultimatecalendar.sync.InvitationCheckOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the tray shows: the pending invitations by day, [loading] until the first read. */
data class InvitationsUiState(
    val days: List<InvitationDay> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false
)

/** One-off messages for the snackbar. */
sealed interface InvitationsEvent {
    /** The answer is stored; the snackbar offers Undo. */
    data class Answered(val invitation: Invitation, val answer: InvitationAnswer) :
        InvitationsEvent

    /** An answer or an undo was not stored. */
    data object AnswerFailed : InvitationsEvent

    /** The event was deleted meanwhile: nothing to answer. */
    data object Gone : InvitationsEvent

    /** Pull to refresh could not read the calendars. */
    data object RefreshFailed : InvitationsEvent
}

/**
 * State of the invitation tray (RF-06). An answer takes effect on screen at once and is written
 * to the source in the background; if the write fails the invitation comes back with a message.
 * Answering also removes the system notification of that invitation.
 */
@HiltViewModel
class InvitationsViewModel @Inject constructor(
    private val inbox: InvitationInbox,
    private val responses: InvitationResponses,
    private val coordinator: InvitationCheckCoordinator,
    private val notifications: InvitationNotificationSurface,
    private val zone: SystemZone
) : ViewModel() {
    /** Answered invitations that stay out of the list while the source catches up. */
    private val answered = MutableStateFlow<Set<InvitationKey>>(emptySet())
    private val refreshing = MutableStateFlow(false)
    private val messages = Channel<InvitationsEvent>(Channel.BUFFERED)

    val events: Flow<InvitationsEvent> = messages.receiveAsFlow()

    val state: StateFlow<InvitationsUiState> = combine(
        inbox.pending().map<List<Invitation>, List<Invitation>?> { it }.onStart { emit(null) },
        answered,
        refreshing
    ) { pending, hidden, isRefreshing ->
        InvitationsUiState(
            days = InvitationDays.group(
                pending.orEmpty().filter { it.key !in hidden },
                zone.current()
            ),
            loading = pending == null,
            refreshing = isRefreshing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InvitationsUiState())

    fun answer(invitation: Invitation, answer: InvitationAnswer) {
        answered.update { it + invitation.key }
        viewModelScope.launch {
            when (responses.respond(invitation.key, answer.status)) {
                ResponseOutcome.Answered -> {
                    forget(invitation.key)
                    messages.send(InvitationsEvent.Answered(invitation, answer))
                }

                ResponseOutcome.Gone -> {
                    forget(invitation.key)
                    messages.send(InvitationsEvent.Gone)
                }

                is ResponseOutcome.Failed -> {
                    answered.update { it - invitation.key }
                    messages.send(InvitationsEvent.AnswerFailed)
                }
            }
        }
    }

    /** Takes back the last answer by making the invitation pending again. */
    fun undo(invitation: Invitation) {
        viewModelScope.launch {
            when (responses.respond(invitation.key, AttendeeStatus.NEEDS_ACTION)) {
                ResponseOutcome.Answered -> {
                    answered.update { it - invitation.key }
                    inbox.refresh()
                }

                ResponseOutcome.Gone -> messages.send(InvitationsEvent.Gone)

                is ResponseOutcome.Failed -> messages.send(InvitationsEvent.AnswerFailed)
            }
        }
    }

    /** Pull to refresh: asks the accounts to sync, checks now and tells if it could not. */
    fun refresh() {
        if (!refreshing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val outcome = coordinator.checkNow()
                inbox.refresh()
                if (outcome is InvitationCheckOutcome.Failed) {
                    messages.send(InvitationsEvent.RefreshFailed)
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    private fun forget(key: InvitationKey) {
        notifications.cancel(key)
        notifications.refreshSummary()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
