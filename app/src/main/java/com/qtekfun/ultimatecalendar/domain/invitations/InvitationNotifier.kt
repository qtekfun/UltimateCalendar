// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

/**
 * Tells the user about invitations (RF-07); T21 implements it with real notifications.
 *
 * A check calls it with what is new, changed, cancelled or answered elsewhere since the last
 * check that completed. If the process dies after the call and before the check records it, the
 * next check calls it again with the same changes, so an implementation must be idempotent (one
 * notification per invitation, replaced rather than repeated) and must not throw.
 */
interface InvitationNotifier {
    suspend fun notify(changes: InvitationChanges)
}
