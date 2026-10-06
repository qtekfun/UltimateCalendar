// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.reminders.EventReminders
import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderEventSource
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderPlanner
import com.qtekfun.ultimatecalendar.domain.reminders.ShownReminder
import com.qtekfun.ultimatecalendar.domain.reminders.SnoozeOption
import com.qtekfun.ultimatecalendar.domain.reminders.Snoozes
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

private class FakeShownReminders : ShownReminders {
    val records = mutableSetOf<ShownReminder>()
    var baseline = false

    override suspend fun all(): Set<ShownReminder> = records.toSet()

    override suspend fun add(reminders: Collection<ShownReminder>) {
        records += reminders
    }

    override suspend fun forgetBefore(instant: Instant) {
        records.removeAll { it.at.isBefore(instant) }
    }

    override suspend fun baselineDone(): Boolean = baseline

    override suspend fun setBaselineDone() {
        baseline = true
    }
}

private class FakeSettings(initial: ReminderSettings = ReminderSettings()) :
    ReminderSettingsSource {
    val state = MutableStateFlow(initial)
    override val settings: Flow<ReminderSettings> = state

    override fun setRobustMode(on: Boolean) {
        state.value = state.value.copy(robustMode = on)
    }
}

private class FakeSnoozed : SnoozedReminders {
    val items = mutableListOf<PlannedReminder>()

    override suspend fun all(): List<PlannedReminder> = items.toList()

    override suspend fun put(reminder: PlannedReminder) {
        items.removeAll { it.id == reminder.id }
        items += reminder
    }

    override suspend fun remove(ids: Collection<Long>) {
        items.removeAll { it.id in ids }
    }

    override suspend fun take(id: Long, at: Instant): Boolean =
        items.removeAll { it.id == id && it.at == at }
}

/** Missed reminders: what shows, what does not, and only once (RF-08). */
class MissedReminderRecoveryTest {
    private val clock = MutableClock(Instant.parse("2026-10-05T10:00:00Z"))
    private val shown = FakeShownReminders()
    private val settings = FakeSettings()
    private val snoozed = FakeSnoozed()
    private val events = mutableListOf<EventReminders>()
    private val source = ReminderEventSource { _, _ -> flowOf(events.toList()) }
    private val brought = mutableListOf<Pair<PlannedReminder, Boolean>>()
    private val notifier = mockk<ReminderNotifier> {
        every { show(any(), any(), any()) } answers
            { brought += firstArg<PlannedReminder>() to secondArg() }
    }
    private val recovery = MissedReminderRecovery(
        source,
        settings,
        shown,
        snoozed,
        notifier,
        ReminderTime(clock) { ZoneOffset.UTC }
    )

    /** An event starting at [start] (UTC) with an alert 10 minutes before. */
    private fun event(title: String, start: String) {
        val at = Instant.parse(start)
        events += EventReminders(
            EventInstance(
                EventId(events.size + 1L),
                CalendarId(1),
                title,
                EventTime.Timed(at, at.plusSeconds(3_600), ZoneOffset.UTC)
            ),
            listOf(Reminder(10))
        )
    }

    @Test
    fun `the first run only records what is past, later runs bring back what was missed`() =
        runTest {
            event("old", "2026-10-05T08:00:00Z")
            recovery.recover()
            assertEquals(emptyList<Pair<PlannedReminder, Boolean>>(), brought)
            assertEquals(true, shown.baseline)

            event("missed", "2026-10-05T09:40:00Z")
            clock.now = clock.now.plusSeconds(60)
            recovery.recover()
            recovery.recover()
            assertEquals(listOf("missed" to true), brought.map { it.first.title to it.second })
        }

    @Test
    fun `a reminder that showed on time is not brought back`() = runTest {
        recovery.recover()
        event("on time", "2026-10-05T09:59:00Z")
        val planned = ReminderPlanner.planAll(events, LocalTime.of(9, 0), ZoneOffset.UTC).single()
        recovery.markShown(planned)
        recovery.recover()
        assertEquals(emptyList<Pair<PlannedReminder, Boolean>>(), brought)
    }

    @Test
    fun `only the chosen window counts, and never when turned off`() = runTest {
        recovery.recover()
        event("yesterday morning", "2026-10-04T08:00:00Z")
        event("this morning", "2026-10-05T08:00:00Z")
        settings.state.value = ReminderSettings(missedWindowHours = 6)
        recovery.recover()
        assertEquals(listOf("this morning"), brought.map { it.first.title })

        event("just now", "2026-10-05T09:50:00Z")
        settings.state.value = ReminderSettings(missedWindowHours = 0)
        recovery.recover()
        assertEquals(listOf("this morning"), brought.map { it.first.title })
    }

    @Test
    fun `records older than any window are forgotten`() = runTest {
        shown.records += ShownReminder(2, clock.now.minus(Duration.ofHours(49)))
        shown.records += ShownReminder(4, clock.now.minus(Duration.ofHours(47)))
        recovery.recover()
        assertEquals(listOf(4L), shown.records.map { it.reminderId })
    }

    /** The reminder of the last [event] after being postponed to [at]. */
    private fun postponed(at: String): PlannedReminder {
        val planned = ReminderPlanner.planAll(events, LocalTime.of(9, 0), ZoneOffset.UTC).last()
        return Snoozes.snooze(
            planned,
            SnoozeOption.FIVE_MINUTES,
            Instant.parse(at).minusSeconds(300)
        )
    }

    @Test
    fun `a postponed reminder whose alarm was lost comes back once`() = runTest {
        recovery.recover()
        event("lunch", "2026-10-05T10:30:00Z")
        val later = postponed("2026-10-05T09:58:00Z")
        snoozed.put(later)
        recovery.recover()
        recovery.recover()
        assertEquals(listOf("lunch" to true), brought.map { it.first.title to it.second })
        assertEquals(emptyList<PlannedReminder>(), snoozed.items)
    }

    @Test
    fun `a postponed reminder that rang is not shown again by the recovery`() = runTest {
        recovery.recover()
        event("lunch", "2026-10-05T10:30:00Z")
        val later = postponed("2026-10-05T09:58:00Z")
        snoozed.put(later)
        recovery.fireSnoozed(later)
        recovery.recover()
        assertEquals(listOf("lunch" to false), brought.map { it.first.title to it.second })
    }

    @Test
    fun `a postponed reminder that was replaced or already shown does not ring`() = runTest {
        event("lunch", "2026-10-05T10:30:00Z")
        val old = postponed("2026-10-05T10:05:00Z")
        snoozed.put(old.copy(at = old.at.plusSeconds(600)))
        recovery.fireSnoozed(old)
        recovery.fireSnoozed(old.copy(id = 1))
        assertEquals(emptyList<Pair<PlannedReminder, Boolean>>(), brought)
    }

    @Test
    fun `a postponed reminder of a cancelled event is forgotten when its time passes`() = runTest {
        event("lunch", "2026-10-05T10:30:00Z")
        val later = postponed("2026-10-05T09:58:00Z")
        events.clear()
        snoozed.put(later)
        recovery.recover()
        assertEquals(emptyList<Pair<PlannedReminder, Boolean>>(), brought)
        assertEquals(emptyList<PlannedReminder>(), snoozed.items)
    }
}
