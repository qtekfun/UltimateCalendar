// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Saves the name and color the user gave a calendar on this phone (RF-02). */
@HiltViewModel
class CalendarLookViewModel @Inject constructor(private val repository: CalendarRepository) :
    ViewModel() {
    /**
     * [pickedColor] null goes back to the source's color; a blank [typedName], to its name. The
     * source itself is never written. The job ends when the write has landed.
     */
    fun save(calendar: CalendarInfo, typedName: String, pickedColor: Int?): Job =
        viewModelScope.launch {
            val current = repository.settings(calendar.id)
            repository.saveSettings(
                calendar.id,
                current.withLook(calendar.displayName, typedName, calendar.color, pickedColor)
            )
        }
}
