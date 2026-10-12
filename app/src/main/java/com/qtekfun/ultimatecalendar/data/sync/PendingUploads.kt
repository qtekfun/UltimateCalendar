// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.EventId

/**
 * What the app wrote to the Android calendar provider and the account's sync adapter has not
 * uploaded yet. An answer to an invitation reaches the organizer only once it is uploaded, so
 * this is how the app knows, without guessing, whether a reply has left the phone.
 */
interface PendingUploads {
    /**
     * The accounts with at least one event still to upload, or null when the provider cannot
     * tell. Accounts nothing syncs (the on-device one) are never listed.
     */
    suspend fun accounts(): Set<CalendarAccount>?

    /**
     * Whether the event [id] has a change still to upload. False for an event of the on-device
     * account, of another source, or that cannot be read.
     */
    suspend fun isPending(id: EventId): Boolean
}
