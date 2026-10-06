// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import kotlinx.coroutines.flow.Flow

/**
 * How many invitations wait for an answer, for the badge on the tray icon (RF-06). Bound to an
 * empty source until T21 connects the `InvitationDetector`.
 */
fun interface PendingInvitations {
    fun count(): Flow<Int>
}
