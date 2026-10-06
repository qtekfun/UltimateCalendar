// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.reminders

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.reminders.DefaultReminders
import com.qtekfun.ultimatecalendar.domain.reminders.EventReminders
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderEventSource
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.ReminderSettingsSource
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * The [ReminderEventSource] of the app (RF-07, RF-08): the occurrences of the one [CalendarSource]
 * (the phone's calendars and the CalDAV ones) with their reminders, read in bulk, whenever the
 * source changes.
 *
 * Which events remind, as the Android calendar decides it:
 * - Only calendars the source reports as visible. `Calendars.VISIBLE` is the provider's own
 *   "selected"; the Android calendar sets no alarm for a calendar that is not, and neither does
 *   the app. The app's own display choices (hiding a calendar in the views) do not change this,
 *   and there is no per-calendar reminder setting.
 * - Not the occurrences the user declined; cancelled ones never reach a source's instances. A
 *   tentative or unanswered invitation does remind.
 * - Only alert reminders count: the planner drops e-mail and SMS ones.
 * - An event that uses the calendar's defaults gets the default reminders of the settings.
 *
 * A burst of changes (a sync writes many rows) is read once, [debounceMs] after the last one. If a
 * read fails the previous answer is repeated (the alarms stay as they are, rather than being
 * cancelled for a hiccup), or nothing when there was none or the permission is missing.
 */
@Singleton
@OptIn(FlowPreview::class)
class CalendarReminderEventSource(
    private val source: CalendarSource,
    private val settings: ReminderSettingsSource,
    private val debounceMs: Long
) : ReminderEventSource {
    @Inject
    constructor(source: CalendarSource, settings: ReminderSettingsSource) :
        this(source, settings, DEBOUNCE_MS)

    override fun observe(from: Instant, to: Instant): Flow<List<EventReminders>> = flow {
        val range = TimeRange(from, to)
        // The first read is at once; only the changes that follow are gathered.
        val triggers = merge(flowOf(Unit), source.changes.debounce(debounceMs))
        val defaults = settings.settings
            .map { it.defaultReminders to it.defaultAllDayReminders }
            .distinctUntilChanged()
        var last: List<EventReminders>? = null
        combine(triggers, defaults) { _, chosen -> chosen }.collect { (timed, allDay) ->
            val read = read(range)
            val answer = if (read != null) {
                DefaultReminders.resolve(read, timed, allDay)
            } else {
                last.orEmpty()
            }
            last = answer
            emit(answer)
        }
    }

    /** The occurrences that may remind, or null when the source failed for now. */
    private suspend fun read(range: TimeRange): List<EventReminders>? {
        val calendars = source.calendars()
        val visible = (calendars as? CalendarResult.Success)?.value
            ?.filter { it.visible }?.map { it.id }?.toSet()
        val occurrences = visible?.let { source.instancesWithReminders(range, it) }
        return when {
            occurrences is CalendarResult.Success ->
                occurrences.value.filter { it.instance.selfStatus != AttendeeStatus.DECLINED }

            // Nothing can be read without the permission: nothing reminds.
            calendars.isPermissionDenied() || occurrences.isPermissionDenied() -> emptyList()

            else -> null
        }
    }

    private fun CalendarResult<*>?.isPermissionDenied() =
        (this as? CalendarResult.Failure)?.error == CalendarError.PermissionDenied

    private companion object {
        const val DEBOUNCE_MS = 1_000L
    }
}
