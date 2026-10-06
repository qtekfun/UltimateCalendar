// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import java.time.Duration

/**
 * The `VALARM`s of a `VEVENT` as minutes before the start. Understood: a relative trigger from
 * the start or from the end, and an absolute `VALUE=DATE-TIME` trigger, all before the start.
 * Alarms after the start, with an unreadable trigger or Apple's placeholder default alarm are
 * kept untouched but not shown.
 */
internal object VeventAlarms {
    private const val SECONDS_PER_MINUTE = 60L

    fun read(vevent: IcsComponent, time: EventTime, zones: IcsZones.Resolver): List<Reminder> =
        vevent.components("VALARM").mapNotNull { reminder(it, time, zones) }

    fun reminder(alarm: IcsComponent, time: EventTime, zones: IcsZones.Resolver): Reminder? {
        val trigger = alarm.property("TRIGGER") ?: return null
        val start = time.startIn(zones.floating)
        val secondsBefore = if (trigger.parameter("VALUE")?.value.equals("DATE-TIME", true)) {
            // Apple marks "the default alarm" with a trigger date in 1976: not a real moment.
            IcsDate.from(trigger)?.takeUnless { isAppleDefault(alarm) }
                ?.let { Duration.between(zones.instant(it), start).seconds }
        } else {
            val offset = IcsDuration.parse(trigger.value)
            val fromEnd = trigger.parameter("RELATED")?.value.equals("END", true)
            val length = if (fromEnd) {
                Duration.between(
                    start,
                    time.endIn(zones.floating)
                ).seconds
            } else {
                0
            }
            offset?.let { -(it + length) }
        }
        return secondsBefore?.takeIf { it >= 0 }?.let {
            Reminder((it / SECONDS_PER_MINUTE).toInt(), method(alarm))
        }
    }

    private fun isAppleDefault(alarm: IcsComponent) =
        alarm.property("X-APPLE-DEFAULT-ALARM")?.value?.trim().equals("TRUE", true)

    private fun method(alarm: IcsComponent) =
        if (alarm.property("ACTION")?.value?.trim().equals("EMAIL", true)) {
            ReminderMethod.EMAIL
        } else {
            ReminderMethod.ALERT
        }

    /** [vevent] with [reminders]; alarms that already say the same keep their original text. */
    fun write(
        vevent: IcsComponent,
        reminders: List<Reminder>,
        time: EventTime,
        zones: IcsZones.Resolver
    ): IcsComponent {
        val existing = vevent.components("VALARM").map { it to reminder(it, time, zones) }
        return vevent.withComponents("VALARM", reconcile(existing, reminders, ::create))
    }

    private fun create(reminder: Reminder): IcsComponent {
        val action = if (reminder.method == ReminderMethod.EMAIL) "EMAIL" else "DISPLAY"
        val properties = buildList {
            add(IcsProperty("ACTION", value = action))
            add(
                IcsProperty(
                    "TRIGGER",
                    value = IcsDuration.format(-reminder.minutesBefore * SECONDS_PER_MINUTE)
                )
            )
            add(IcsProperty("DESCRIPTION", value = "Reminder"))
            if (reminder.method ==
                ReminderMethod.EMAIL
            ) {
                add(IcsProperty("SUMMARY", value = "Reminder"))
            }
        }
        return IcsComponent("VALARM", properties)
    }
}
