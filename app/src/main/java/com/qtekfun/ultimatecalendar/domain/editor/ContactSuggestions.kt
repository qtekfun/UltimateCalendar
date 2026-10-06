// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

/** An address from the phone's contacts, offered while the user types a guest. */
data class ContactSuggestion(val name: String?, val email: String)

/**
 * Suggestions for the guests field (RF-05). They exist only when the user granted the optional
 * contacts permission; without it [find] returns nothing and nothing is ever read.
 */
interface ContactSuggestions {
    /** Whether the contacts permission is granted right now. */
    fun isAvailable(): Boolean

    /** The contacts whose name or address matches [query], best first. */
    suspend fun find(query: String): List<ContactSuggestion>
}
