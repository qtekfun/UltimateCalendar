// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.contacts

import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.editor.ContactSuggestion
import com.qtekfun.ultimatecalendar.domain.editor.ContactSuggestions
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** One address of a contact as the phone stores it. */
data class ContactRow(val name: String?, val email: String?)

/** The phone's contacts, behind a seam so the ranking is testable without Android. */
interface ContactsGateway {
    fun isGranted(): Boolean

    /** The addresses of the contacts matching [query] (it is only called when granted). */
    fun search(query: String, limit: Int): List<ContactRow>
}

/**
 * The contacts suggestions: addresses that are valid, once each, never more than [MAX], and
 * nothing at all while the permission is missing or the query is blank.
 */
class DeviceContactSuggestions @Inject constructor(
    private val gateway: ContactsGateway,
    @IoDispatcher private val io: CoroutineDispatcher
) : ContactSuggestions {
    override fun isAvailable(): Boolean = gateway.isGranted()

    override suspend fun find(query: String): List<ContactSuggestion> {
        val text = query.trim()
        return if (text.isEmpty() || !gateway.isGranted()) {
            emptyList()
        } else {
            withContext(io) { gateway.search(text, FETCH) }.mapNotNull { row ->
                SettingsRules.alias(row.email.orEmpty())?.let { address ->
                    ContactSuggestion(row.name?.trim()?.ifEmpty { null }, Attendee.normalize(address))
                }
            }.distinctBy { it.email }.take(MAX)
        }
    }

    companion object {
        const val MAX = 5

        /** More rows than shown, as several rows of one contact share an address. */
        private const val FETCH = 20
    }
}
