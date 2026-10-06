// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The settings screen's state. Each setting is one call: the values are mended by the
 * repository and [SettingsRules], so there is nothing to compute here.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) :
    ViewModel() {
    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_MS),
            repository.current()
        )

    fun update(transform: (AppSettings) -> AppSettings) = repository.update(transform)

    /** Adds an own address; false if [text] is not an email address. */
    fun addOwnEmail(text: String): Boolean {
        val address = SettingsRules.alias(text) ?: return false
        update { it.copy(ownEmails = it.ownEmails + address) }
        return true
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
