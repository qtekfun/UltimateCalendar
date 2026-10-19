// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.localnet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The host of the CalDAV account's server, for the local network check; null without an account
 * (a calendar kept only on the phone never needs the permission).
 */
@HiltViewModel
class ServerHostViewModel @Inject constructor(session: AccountSession) : ViewModel() {
    val host: StateFlow<String?> = session.activeAccount
        .map { account -> account?.let { LocalNetwork.hostOf(it.serverUrl) } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            session.activeAccount.value?.let { LocalNetwork.hostOf(it.serverUrl) }
        )
}
