// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.sync.InvitationSyncs
import com.qtekfun.ultimatecalendar.sync.ManualRefresh
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The shell's background news (RF-06): whether a refresh the user asked for is running, how it
 * went, and invitations that were saved but wait for the account's own sync. The work is
 * [ManualRefresh]'s; this only carries it to the screen.
 */
@HiltViewModel
class RefreshViewModel @Inject constructor(
    private val refresher: ManualRefresh,
    invitationSyncs: InvitationSyncs
) : ViewModel() {
    private val notes = Channel<ShellMessage>(Channel.BUFFERED)

    /** A refresh is running. */
    val refreshing: StateFlow<Boolean> get() = refresher.running

    /** What to tell the user: how a refresh went, and invitations that wait for the next sync. */
    val messages: Flow<ShellMessage> = notes.receiveAsFlow()

    init {
        viewModelScope.launch {
            invitationSyncs.unsent.collect { notes.send(ShellMessage.InvitationsLater) }
        }
    }

    /** Refreshes everything the user can see, and says how it went; a second tap is ignored. */
    fun refresh() {
        viewModelScope.launch {
            refresher.refresh()?.let { notes.send(ShellMessage.Refreshed(it)) }
        }
    }
}
