// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.Context
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.firstrun.TestDelivery
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The test reminder of the wizard (RF-01, RF-08): an alarm a minute ahead, set exactly as real
 * reminders are, so it shows whether this phone delivers them on time. When it arrives is kept
 * to tell the user.
 */
@Singleton
class TestReminder @Inject constructor(
    @ApplicationContext context: Context,
    private val scheduler: ReminderScheduler,
    private val settings: ReminderSettingsSource,
    private val clock: Clock
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    suspend fun send(title: String) {
        val at = clock.instant().plus(DELAY)
        preferences.edit {
            putLong(KEY_SCHEDULED, at.toEpochMilli())
            remove(KEY_ARRIVED)
        }
        val reminder = PlannedReminder(
            id = ID,
            eventId = EventId(0),
            calendarId = CalendarId(0),
            title = title,
            location = null,
            start = at,
            allDay = false,
            at = at
        )
        scheduler.scheduleOne(reminder, settings.settings.first().alarmClock)
    }

    fun arrived() = preferences.edit { putLong(KEY_ARRIVED, clock.instant().toEpochMilli()) }

    fun delivery(): TestDelivery? =
        TestDelivery.of(instant(KEY_SCHEDULED), instant(KEY_ARRIVED), clock.instant())

    private fun instant(key: String): Instant? =
        preferences.getLong(key, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    companion object {
        /** Planned reminder ids are Ints (hashes), so this one never collides with them. */
        const val ID = Long.MIN_VALUE

        private val DELAY: Duration = Duration.ofMinutes(1)
        private const val PREFERENCES = "test_reminder"
        private const val KEY_SCHEDULED = "scheduled"
        private const val KEY_ARRIVED = "arrived"
    }
}
