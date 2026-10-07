// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

/**
 * The extra addresses the user answers invitations with (the aliases of Settings): the same ones
 * the invitation check and the detail screen count as "me", so an invitation sent to an alias can
 * also be answered.
 */
fun interface OwnAddresses {
    suspend fun addresses(): Set<String>
}
