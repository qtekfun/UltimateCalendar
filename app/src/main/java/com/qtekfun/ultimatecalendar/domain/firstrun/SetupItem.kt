// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

/** A step of the wizard and where it stands. */
data class SetupItem(val step: SetupStep, val state: State) {
    enum class State {
        /** The system already allows it. */
        DONE,

        /** Still to do; the app can tell when it is. */
        TODO,

        /** Advice only: the app cannot know whether the user did it. */
        ADVICE
    }
}
