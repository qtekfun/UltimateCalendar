// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import java.time.Instant

/** A [LastSyncStore] that lives in memory. */
class InMemoryLastSyncStore(var at: Instant? = null) : LastSyncStore {
    override fun lastOk(): Instant? = at

    override fun recordOk(at: Instant) {
        this.at = at
    }

    override fun clear() {
        at = null
    }
}
