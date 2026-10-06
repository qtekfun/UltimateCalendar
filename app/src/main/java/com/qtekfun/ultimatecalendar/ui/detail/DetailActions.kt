// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus

/** What the detail can ask for; each is a plain callback so the body stays stateless. */
data class DetailActions(
    val onRespond: (AttendeeStatus) -> Unit = {},
    val onOpenUri: (String) -> Unit = {}
)
