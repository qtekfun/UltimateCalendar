// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope

/** What the guests section asks of the editor. */
internal data class GuestActions(
    val onAdd: (String) -> Boolean,
    val onType: (String) -> Unit,
    val onContactsAnswer: (String) -> Unit,
    val onClearInvalid: () -> Unit
)

/** Everything the screen can ask of the ViewModel; the screen itself has no state. */
internal data class EditorActions(
    val onEdit: ((EventForm) -> EventForm) -> Unit,
    val onSave: (RecurrenceScope?) -> Unit,
    val onLeave: (Boolean) -> Unit,
    val onDismiss: (Dismissal) -> Unit,
    val guests: GuestActions
)
