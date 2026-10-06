// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.contacts.ContactsGateway
import com.qtekfun.ultimatecalendar.data.contacts.ContentResolverContactsGateway
import com.qtekfun.ultimatecalendar.data.contacts.DeviceContactSuggestions
import com.qtekfun.ultimatecalendar.domain.editor.ContactSuggestions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The optional contact suggestions of the guests field (RF-05). */
@Module
@InstallIn(SingletonComponent::class)
interface ContactsModule {
    @Binds
    fun suggestions(suggestions: DeviceContactSuggestions): ContactSuggestions

    @Binds
    fun gateway(gateway: ContentResolverContactsGateway): ContactsGateway
}
