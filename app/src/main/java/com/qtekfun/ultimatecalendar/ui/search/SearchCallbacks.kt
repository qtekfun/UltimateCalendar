// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.search

import com.qtekfun.ultimatecalendar.domain.model.EventInstance

/** What the search screen asks of its owner. */
internal data class SearchCallbacks(
    val onBack: () -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onSubmit: (String) -> Unit = {},
    val onIncludeHidden: (Boolean) -> Unit = {},
    val onOpenEvent: (EventInstance) -> Unit = {},
    val onForgetRecent: (String) -> Unit = {},
    val onClearRecent: () -> Unit = {}
)
