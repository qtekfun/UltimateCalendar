// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.subscription

import com.qtekfun.ultimatecalendar.data.local.entity.SubscriptionEntity
import com.qtekfun.ultimatecalendar.data.source.SubscriptionIds
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent

/** Rows of the subscriptions as the domain sees them, with the ids of [SubscriptionIds]. */
internal object SubscriptionMapping {
    /** Not shown as such: the drawer names the group itself (see `CalendarAccount.isSubscription`). */
    const val ACCOUNT_NAME = "subscriptions"

    private val account = CalendarAccount(ACCOUNT_NAME, CalendarAccount.SUBSCRIPTION_TYPE)

    /** A subscription is a calendar the user can only read, and never answers anything in. */
    fun calendar(row: SubscriptionEntity) = CalendarInfo(
        id = SubscriptionIds.calendar(row.id),
        account = account,
        displayName = row.name,
        color = row.color,
        access = CalendarAccess.READ,
        visible = true,
        ownerEmail = null
    )

    fun event(event: Event) = event.copy(
        id = SubscriptionIds.event(event.id.value),
        calendarId = SubscriptionIds.calendar(event.calendarId.value)
    )

    fun instance(instance: EventInstance) = instance.copy(
        eventId = SubscriptionIds.event(instance.eventId.value),
        calendarId = SubscriptionIds.calendar(instance.calendarId.value)
    )

    fun searchable(found: SearchableEvent) = found.copy(
        eventId = SubscriptionIds.event(found.eventId.value),
        calendarId = SubscriptionIds.calendar(found.calendarId.value)
    )
}
