// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.domain.auth.LoginFlow
import com.qtekfun.ultimatecalendar.domain.auth.LoginState
import com.qtekfun.ultimatecalendar.domain.auth.LoginUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the login of the CalDAV connection (RF-12): the typed address and the step of Login Flow
 * v2. The rules live in [LoginUiState] and the flow in [LoginFlow]; this only joins them. Once
 * the flow signs in, the account state changes and the screen is replaced by the account.
 */
@HiltViewModel
class CalDavLoginViewModel @Inject constructor(private val loginFlow: LoginFlow) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun onServerChange(server: String) = mutableState.update { it.withServer(server) }

    fun connect() {
        val server = state.value.server
        if (!state.value.canSubmit) return
        job?.cancel()
        job = viewModelScope.launch {
            loginFlow.login(server).collect { step ->
                // Signed in: the account takes the screen over, and a later sign-out starts from
                // an empty form.
                mutableState.update {
                    if (step is LoginState.LoggedIn) LoginUiState() else it.withStep(step)
                }
            }
        }
    }

    /** Stops polling; the token simply expires on the server. */
    fun cancel() {
        job?.cancel()
        mutableState.update { it.cancelled() }
    }
}
