// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.reminders.EventReminders
import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderEventSource
import com.qtekfun.ultimatecalendar.domain.reminders.ShownReminder
import com.qtekfun.ultimatecalendar.domain.reminders.SnoozeOption
import com.qtekfun.ultimatecalendar.domain.reminders.Snoozes
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.MissedReminderRecovery
import com.qtekfun.ultimatecalendar.notify.ReminderCoordinator
import com.qtekfun.ultimatecalendar.notify.ReminderHeartbeat
import com.qtekfun.ultimatecalendar.notify.ReminderNotifier
import com.qtekfun.ultimatecalendar.notify.ReminderScheduler
import com.qtekfun.ultimatecalendar.notify.ReminderSettings
import com.qtekfun.ultimatecalendar.notify.ReminderSettingsSource
import com.qtekfun.ultimatecalendar.notify.ReminderTime
import com.qtekfun.ultimatecalendar.notify.ShownReminders
import com.qtekfun.ultimatecalendar.notify.SnoozedReminders
import com.qtekfun.ultimatecalendar.sync.CheckFixtures
import com.qtekfun.ultimatecalendar.sync.MutableClock
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** The log of reminders shown, as the phone keeps it between runs of the app. */
class FakeShownReminders : ShownReminders {
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

/** The postponed reminders, as the phone keeps them between runs of the app. */
class FakeSnoozedReminders : SnoozedReminders {
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

class FakeReminderSettings(initial: ReminderSettings = ReminderSettings()) :
    ReminderSettingsSource {
    val state = MutableStateFlow(initial)
    override val settings: Flow<ReminderSettings> = state

    override fun setRobustMode(on: Boolean) {
        state.value = state.value.copy(robustMode = on)
    }
}

/** A notification that reached the shade: [missed] is the "did not arrive on time" wording. */
data class Shown(val reminder: PlannedReminder, val missed: Boolean, val clock: Instant)

/**
 * The phone around the reminders: the real [ReminderCoordinator], [MissedReminderRecovery] and
 * [ReminderHeartbeat] over the [FakeCalendarSource], with fakes only at the Android edges. The
 * alarm manager is [alarms] (what is set right now), the notification shade is [shade], the
 * clock and the time zone move by hand. What the phone keeps on disk ([shownLog], [snoozed],
 * [settings]) survives [kill] and [reboot]; alarms survive [kill] but not [reboot].
 */
@Suppress("TooManyFunctions")
class ReminderRig(
    start: Instant,
    var zone: ZoneId,
    private val dispatcher: CoroutineDispatcher,
    initialSettings: ReminderSettings = ReminderSettings()
) {
    val clock = MutableClock(start)
    val source = FakeCalendarSource(listOf(CheckFixtures.calendar(1)))
    val shownLog = FakeShownReminders()
    val snoozed = FakeSnoozedReminders()
    val settings = FakeReminderSettings(initialSettings)
    val shade = mutableListOf<Shown>()

    /** What the alarm manager holds, soonest first. */
    var alarms: List<PlannedReminder> = emptyList()
        private set

    private val time = ReminderTime(clock) { zone }
    private var job: Job? = null
    private lateinit var recovery: MissedReminderRecovery
    private lateinit var coordinator: ReminderCoordinator
    private lateinit var heartbeat: ReminderHeartbeat

    private val events = ReminderEventSource { from, to ->
        source.changes.onStart { emit(Unit) }.map {
            val range = TimeRange(from, to)
            (source.instances(range) as CalendarResult.Success).value.map { instance ->
                val event = (source.event(instance.eventId) as CalendarResult.Success).value
                EventReminders(instance, event.reminders)
            }
        }
    }

    private val notifier = mockk<ReminderNotifier> {
        every { show(any(), any(), any()) } answers {
            shade += Shown(firstArg(), secondArg(), clock.instant())
        }
    }

    private val scheduler = mockk<ReminderScheduler> {
        every { schedule(any(), any()) } answers { alarms = firstArg() }
        every { scheduleOne(any(), any()) } answers {
            val one = firstArg<PlannedReminder>()
            alarms = (alarms.filter { it.id != one.id } + one).sortedBy { it.at }
        }
    }

    /** The app process starts (the application's `onCreate`): plans and recovers. */
    fun start() {
        job?.cancel()
        val process = Job()
        job = process
        recovery = MissedReminderRecovery(events, settings, shownLog, snoozed, notifier, time)
        coordinator =
            ReminderCoordinator(events, settings, scheduler, recovery, snoozed, shownLog, time)
        heartbeat = ReminderHeartbeat(coordinator, recovery)
        coordinator.start(CoroutineScope(dispatcher + process))
    }

    /** The system kills the process; its alarms stay. */
    fun kill() {
        job?.cancel()
    }

    /** The phone restarts (or the app is updated and the system drops its alarms). */
    fun reboot() {
        kill()
        alarms = emptyList()
        start()
    }

    /** A phone that frees the app's alarms while it sleeps, as some vendors do. */
    fun loseAlarms() {
        alarms = emptyList()
    }

    /** The phone's zone changes; `BootReceiver` gets TIMEZONE_CHANGED and plans again. */
    fun changeZone(to: ZoneId) {
        zone = to
        coordinator.refresh()
    }

    /** The clock jumps to [to] (the user or the network set it); no broadcast has arrived yet. */
    fun jumpClock(to: Instant) {
        clock.now = to
    }

    /** `BootReceiver` got TIME_SET (or any of its broadcasts): every reminder is planned again. */
    fun refresh() = coordinator.refresh()

    /** The heartbeat's alarm rang. */
    suspend fun beat() = heartbeat.beat()

    /** The user opens the app after a while: the recovery runs. */
    suspend fun openApp() = recovery.recover()

    /**
     * Time goes by until [to]: each alarm that is set rings at its instant, as `ReminderReceiver`
     * does (a postponed one through the recovery's own path). The process need not be alive.
     */
    suspend fun advanceTo(to: Instant) {
        while (true) {
            val next = alarms.firstOrNull { !it.at.isAfter(to) } ?: break
            alarms = alarms - next
            if (next.at.isAfter(clock.now)) clock.now = next.at
            ring(next)
        }
        if (to.isAfter(clock.now)) clock.now = to
    }

    private suspend fun ring(reminder: PlannedReminder) {
        if (Snoozes.isSnooze(reminder.id)) {
            recovery.fireSnoozed(reminder)
        } else {
            notifier.show(reminder, false)
            recovery.markShown(reminder)
        }
        recovery.recover()
    }

    /** The user taps a snooze button of the notification of [reminder]. */
    suspend fun snooze(reminder: PlannedReminder, option: SnoozeOption) {
        val later = Snoozes.snooze(reminder, option, clock.instant())
        snoozed.put(later)
        scheduler.scheduleOne(later, false)
        coordinator.replan()
    }

    /** The instants of the alarms set, in order. */
    fun alarmTimes(): List<Instant> = alarms.map { it.at }

    /** Titles that were shown, in order of appearance. */
    fun shownTitles(): List<String> = shade.map { it.reminder.title }

    /** A timed event starting at [start] with an alert [minutes] before, each. */
    suspend fun timed(
        title: String,
        start: ZonedDateTime,
        vararg minutes: Int,
        rrule: String? = null
    ): EventId = create(
        EventDraft(
            calendarId = CalendarId(1),
            title = title,
            time = EventTime.Timed(
                start.toInstant(),
                start.toInstant().plusSeconds(HOUR),
                start.zone
            ),
            rrule = rrule,
            reminders = minutes.map { Reminder(it) }
        )
    )

    /** An all-day event on [day] with an alert [minutes] before midnight, each. */
    suspend fun allDay(title: String, day: LocalDate, vararg minutes: Int): EventId = create(
        EventDraft(
            calendarId = CalendarId(1),
            title = title,
            time = EventTime.AllDay(day, day.plusDays(1)),
            reminders = minutes.map { Reminder(it) }
        )
    )

    private suspend fun create(draft: EventDraft): EventId =
        (source.create(draft) as CalendarResult.Success).value

    private companion object {
        const val HOUR = 3_600L
    }
}

/** Runs [body] on a phone that starts at [start] in [zone], with the app already running. */
@OptIn(ExperimentalCoroutinesApi::class)
fun rigTest(
    start: String,
    zone: String,
    settings: ReminderSettings = ReminderSettings(),
    body: suspend ReminderRig.() -> Unit
) = runTest {
    val rig = ReminderRig(
        Instant.parse(start),
        ZoneId.of(zone),
        UnconfinedTestDispatcher(testScheduler),
        settings
    )
    rig.start()
    try {
        rig.body()
    } finally {
        rig.kill()
    }
}
