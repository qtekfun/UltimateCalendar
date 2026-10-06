// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.firstrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.domain.firstrun.SetupPlan
import com.qtekfun.ultimatecalendar.domain.firstrun.SetupStatus
import com.qtekfun.ultimatecalendar.domain.firstrun.TestDelivery
import com.qtekfun.ultimatecalendar.notify.TestReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Holds what the wizard shows: the system's state, the plan for it and the test reminder. */
@HiltViewModel
class FirstRunViewModel @Inject constructor(
    private val setup: SystemSetup,
    private val test: TestReminder
) : ViewModel() {
    private val mutableStatus = MutableStateFlow<SetupStatus?>(null)
    val status: StateFlow<SetupStatus?> = mutableStatus.asStateFlow()

    private val mutableDelivery = MutableStateFlow(test.delivery())
    val delivery: StateFlow<TestDelivery?> = mutableDelivery.asStateFlow()

    /** Reads the system again: permissions change in other screens. */
    fun refresh() {
        viewModelScope.launch { mutableStatus.value = setup.status() }
    }

    fun plan(status: SetupStatus) = SetupPlan.of(status)

    fun label(packageName: String): String = setup.label(packageName)

    fun sendTest(title: String) {
        viewModelScope.launch {
            test.send(title)
            checkTest()
        }
    }

    /** Reads it again: it arrives through an alarm, outside the screen. */
    fun checkTest() {
        mutableDelivery.value = test.delivery()
    }
}
