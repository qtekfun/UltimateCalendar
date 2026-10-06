// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo

/** The calendars of one account, as the drawer lists them (RF-02). */
data class AccountCalendars(val account: CalendarAccount, val calendars: List<CalendarInfo>) {
    companion object {
        /** Groups by account, accounts and calendars in alphabetical order. */
        fun group(calendars: List<CalendarInfo>): List<AccountCalendars> = calendars
            .groupBy { it.account }
            .map { (account, list) ->
                AccountCalendars(account, list.sortedBy { it.displayName.lowercase() })
            }
            .sortedWith(compareBy({ it.account.name.lowercase() }, { it.account.type }))
    }
}
