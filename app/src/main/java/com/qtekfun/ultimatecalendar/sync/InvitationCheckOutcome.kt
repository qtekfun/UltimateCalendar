// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.domain.invitations.InvitationChanges
import com.qtekfun.ultimatecalendar.domain.result.CalendarError

sealed interface InvitationCheckOutcome {
    /** The check ran to the end and recorded its result; [pending] invitations await an answer. */
    data class Done(val changes: InvitationChanges, val pending: Int) : InvitationCheckOutcome

    /** Nothing was recorded: the next check starts from the same point and notifies it all. */
    data class Failed(val error: CalendarError) : InvitationCheckOutcome
}
