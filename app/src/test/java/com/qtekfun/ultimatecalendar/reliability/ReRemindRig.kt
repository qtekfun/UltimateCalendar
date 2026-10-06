// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.invitations.ReRemindLog
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAlert
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindEntry
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindOption
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.InvitationNotificationSurface
import com.qtekfun.ultimatecalendar.notify.InvitationReReminderAlarmHandler
import com.qtekfun.ultimatecalendar.notify.InvitationReReminderAlarms
import com.qtekfun.ultimatecalendar.notify.ReRemindCoordinator
import com.qtekfun.ultimatecalendar.notify.ReminderSettings
import com.qtekfun.ultimatecalendar.notify.ReminderTime
import com.qtekfun.ultimatecalendar.sync.CheckFixtures
import com.qtekfun.ultimatecalendar.sync.FixedCheckSettings
import com.qtekfun.ultimatecalendar.sync.InvitationChecker
import com.qtekfun.ultimatecalendar.sync.MutableClock
import com.qtekfun.ultimatecalendar.sync.RecordingNotifier
import com.qtekfun.ultimatecalendar.sync.RecordingSyncRequester
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** What reached the notification shade: the invitation and how loudly. */
data class ShownInvitation(val title: String, val alert: InvitationAlert, val clock: Instant)

/** The alarm manager for the re-reminders: what is set right now. */
class FakeReReminderAlarms : InvitationReReminderAlarms {
    var set: List<ReRemindEntry> = emptyList()

    override fun schedule(alarms: List<ReRemindEntry>, alarmClock: Boolean) {
        set = alarms
    }
}

/**
 * The phone around the re-reminders of unanswered invitations (T40): the real
 * [ReRemindCoordinator], [InvitationChecker], [InvitationReReminderAlarmHandler], Room and the
 * fake calendar, with fakes only at the Android edges (alarms, notification shade). What is in
 * Room and in the calendar survives [kill] and [reboot]; alarms survive [kill] but not [reboot].
 */
class ReRemindRig(
    start: Instant,
    var zone: ZoneId,
    private val dispatcher: CoroutineDispatcher,
    option: ReRemindOption,
    missedWindowHours: Int = DEFAULT_WINDOW_HOURS
) {
    val clock = MutableClock(start)
    val source = FakeCalendarSource(listOf(CheckFixtures.calendar(1)))
    val settings = FakeReminderSettings(
        ReminderSettings(reRemind = option, missedWindowHours = missedWindowHours)
    )
    val shade = mutableListOf<ShownInvitation>()
    private val alarmManager = FakeReReminderAlarms()
    private val database: UltimateCalendarDatabase = inMemoryDatabase(Dispatchers.Unconfined)
    private val notified =
        NotifiedInvitations(database.notifiedInvitationDao(), Dispatchers.Unconfined)
    private val log = ReRemindLog(database.reRemindDao(), Dispatchers.Unconfined)
    private val time = ReminderTime(clock) { zone }
    private var job: Job? = null
    private var alive = false
    private lateinit var coordinator: ReRemindCoordinator
    private lateinit var checker: InvitationChecker
    private lateinit var handler: InvitationReReminderAlarmHandler

    private val surface = object : InvitationNotificationSurface {
        override fun show(invitation: Invitation, alert: InvitationAlert) {
            shade += ShownInvitation(invitation.title, alert, clock.instant())
        }

        override fun showAnswerFailed(invitation: Invitation) = Unit

        override fun showMoved(invitation: Invitation) = Unit

        override fun showCancelled(invitation: Invitation) = Unit

        override fun clearChanges(key: InvitationKey) = Unit

        override fun cancel(key: InvitationKey) = Unit

        override fun refreshSummary() = Unit
    }

    /** What the alarm manager holds, soonest first. */
    val alarms: List<ReRemindEntry> get() = alarmManager.set

    /** The app process starts: it plans from what was last known. */
    fun start() {
        job?.cancel()
        val process = Job()
        job = process
        alive = true
        coordinator = ReRemindCoordinator(log, notified, settings, time, alarmManager, surface)
        checker = InvitationChecker(
            source,
            RecordingSyncRequester(),
            notified,
            RecordingNotifier(),
            FixedCheckSettings(),
            clock,
            Dispatchers.Unconfined,
            coordinator
        )
        handler = InvitationReReminderAlarmHandler(checker, coordinator)
        coordinator.start(CoroutineScope(dispatcher + process))
    }

    /** The system kills the process; its alarms stay. */
    fun kill() {
        job?.cancel()
        alive = false
    }

    /** The phone restarts: no alarm survives, the app starts and plans them again. */
    fun reboot() {
        kill()
        alarmManager.set = emptyList()
        start()
    }

    /** The phone's zone changes; `BootReceiver` gets TIMEZONE_CHANGED. */
    fun changeZone(to: ZoneId) {
        zone = to
        coordinator.refresh()
    }

    /** The clock jumps to [to]; the system broadcast comes next ([refresh]). */
    fun jumpClock(to: Instant) {
        clock.now = to
    }

    /** `BootReceiver` got TIME_SET (or any of its broadcasts). */
    fun refresh() = coordinator.refresh()

    /** A phone that frees the app's alarms while it sleeps. */
    fun loseAlarms() {
        alarmManager.set = emptyList()
    }

    /** The user opens the app: a check runs and the extra reminders follow it. */
    suspend fun check() = checker.check(requestSync = false)

    /** The heartbeat's beat: the extra reminders are planned again from what was last known. */
    suspend fun beat() = coordinator.reconcileStored()

    /** The user changes the option in Settings. */
    fun choose(option: ReRemindOption) {
        settings.state.value = settings.state.value.copy(reRemind = option)
    }

    /** Time goes by until [to]; each alarm set rings at its instant, even with the app dead. */
    suspend fun advanceTo(to: Instant) {
        while (true) {
            val next = alarms.firstOrNull { !it.at.isAfter(to) } ?: break
            alarmManager.set = alarms - next
            if (next.at.isAfter(clock.now)) clock.now = next.at
            if (!alive) start()
            handler.onAlarm()
        }
        if (to.isAfter(clock.now)) clock.now = to
    }

    /** Titles that were shown, in order of appearance. */
    fun shownTitles(): List<String> = shade.map { it.title }

    /** The instants of the alarms set, in order. */
    fun alarmTimes(): List<Instant> = alarms.map { it.at }

    /** An invitation to a timed event starting at [start]. */
    suspend fun invite(title: String, start: ZonedDateTime): EventId = create(
        draft(
            title,
            EventTime.Timed(start.toInstant(), start.toInstant().plusSeconds(HOUR), start.zone)
        )
    )

    /** An invitation to an all-day event on [day]. */
    suspend fun inviteAllDay(title: String, day: LocalDate): EventId =
        create(draft(title, EventTime.AllDay(day, day.plusDays(1))))

    suspend fun event(id: EventId): Event = (source.event(id) as CalendarResult.Success).value

    fun close() {
        job?.cancel()
        database.close()
    }

    private fun draft(title: String, time: EventTime) = EventDraft(
        calendarId = CalendarId(1),
        title = title,
        time = time,
        attendees = listOf(Attendee.of(CheckFixtures.ME))
    )

    private suspend fun create(draft: EventDraft): EventId =
        (source.create(draft) as CalendarResult.Success).value

    /** The user answers from another app or device. */
    suspend fun answerElsewhere(id: EventId) {
        source.respond(id, AttendeeStatus.ACCEPTED)
    }

    private companion object {
        const val HOUR = 3_600L
        const val DEFAULT_WINDOW_HOURS = 24
    }
}

/** Runs [body] on a phone that starts at [start] in [zone] with the app running. */
@OptIn(ExperimentalCoroutinesApi::class)
fun reRemindTest(
    start: String,
    zone: String,
    option: ReRemindOption,
    missedWindowHours: Int = 24,
    body: suspend ReRemindRig.() -> Unit
) = runTest {
    val rig = ReRemindRig(
        Instant.parse(start),
        ZoneId.of(zone),
        UnconfinedTestDispatcher(testScheduler),
        option,
        missedWindowHours
    )
    rig.start()
    try {
        rig.body()
    } finally {
        rig.close()
    }
}
