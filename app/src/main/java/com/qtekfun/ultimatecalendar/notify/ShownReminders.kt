// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.domain.reminders.ShownReminder
import java.time.Instant

/** The log of reminders that were shown, local to this phone (RF-08). */
interface ShownReminders {
    suspend fun all(): Set<ShownReminder>

    suspend fun add(reminders: Collection<ShownReminder>)

    /** Forgets the records of reminders that were for a time before [instant]. */
    suspend fun forgetBefore(instant: Instant)

    /** Whether the first recovery already recorded what was past (see [MissedReminderRecovery]). */
    suspend fun baselineDone(): Boolean

    suspend fun setBaselineDone()
}
