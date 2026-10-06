// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.navigation

import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.editor.EditorRequest

/** Search or the tray is open: they sit on top of whatever is below, so the detail is not in a pane. */
internal fun NavState.overlayOpen() = search || invitations

/** Search covers the shell by itself: no dialogs, and nothing open from it. */
internal fun NavState.searchIsScreen(dialogs: Boolean) =
    !dialogs && search && !eventDetail && !newEvent

internal fun NavState.editorOrNew() = editorRequest ?: EditorRequest.New()

internal fun NavState.open(ref: EventRef) {
    detailRef = ref
    eventDetail = true
}
