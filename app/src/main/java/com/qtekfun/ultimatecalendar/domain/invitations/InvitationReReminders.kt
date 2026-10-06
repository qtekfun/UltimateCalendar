// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

/**
 * Keeps the extra reminders of the invitations nobody answered (T40) in step with what is
 * pending. A check calls it with every invitation still pending after it ran, so an answer given
 * anywhere, a cancellation or a move cancels or replaces the reminders. An implementation must
 * be safe to call again with the same list (nothing shows twice) and must not throw.
 */
fun interface InvitationReReminders {
    suspend fun reconcile(pending: List<Invitation>)
}
