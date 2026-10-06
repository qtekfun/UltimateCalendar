// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavAccountRepository
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavAccountState
import com.qtekfun.ultimatecalendar.domain.caldav.CalDavCalendarItem
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.sync.engine.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the CalDAV account screen shows: the account, its calendars and the sync. [account] is
 * null until the first read; [AccountUiState.signedOut] chooses between the login and the account.
 */
data class AccountUiState(
    val account: CalDavAccountState? = null,
    val calendars: List<CalDavCalendarItem> = emptyList(),
    val status: SyncStatus = SyncStatus(SyncStatus.Phase.NEVER, null),
    val signingOut: Boolean = false
) {
    val signedOut: Boolean get() = account == CalDavAccountState.SignedOut
}

/** Holds the account screen (RF-12). Everything it does is one call to the repository. */
@HiltViewModel
class CalDavAccountViewModel @Inject constructor(private val repository: CalDavAccountRepository) :
    ViewModel() {
    private val signingOut = MutableStateFlow(false)

    val state: StateFlow<AccountUiState> = combine(
        repository.state,
        repository.calendars,
        repository.syncStatus,
        signingOut
    ) { account, calendars, status, busy ->
        AccountUiState(account, calendars, status, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), AccountUiState())

    /** "Sync now": the unlimited path, run at once; the status tells how it went. */
    fun syncNow() {
        viewModelScope.launch { repository.syncNow() }
    }

    fun setEnabled(id: CalendarId, enabled: Boolean) {
        viewModelScope.launch { repository.setCalendarEnabled(id, enabled) }
    }

    fun signOut() {
        if (signingOut.value) return
        signingOut.update { true }
        viewModelScope.launch {
            try {
                repository.signOut()
            } finally {
                signingOut.update { false }
            }
        }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
