// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDav
import com.qtekfun.ultimatecalendar.sync.queue.OperationQueue
import com.qtekfun.ultimatecalendar.sync.queue.ProcessResult
import java.time.Clock
import javax.inject.Inject

/** Sends the queued local changes of an account (SPEC §5). */
class PushSync @Inject constructor(
    private val database: UltimateCalendarDatabase,
    private val queue: OperationQueue,
    private val clock: Clock
) {
    suspend fun push(dav: CalDav, accountId: Long): ProcessResult = queue.process(
        accountId,
        EventOperationExecutor(
            dav,
            database,
            EventMerger(database, queue, clock.zone),
            clock
        )
    )
}
