// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.chains

import android.app.Notification
import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatecalendar.AppStartup
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.flows.FlowTest
import com.qtekfun.ultimatecalendar.notify.ReminderScheduler
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After

/**
 * The chains that only work if everything between the calendar provider and the system is really
 * connected in the app graph: an event in the provider, the coordinators that [AppStartup] starts
 * (the very list the app starts when its process does), the alarms and notifications the system
 * ends up with. The tests write to the provider the way a sync adapter does and read the system's
 * side with `dumpsys alarm` and the notification manager, so none of them agrees with a bug by
 * sharing it. They run on an emulator, in the LOCAL test account of [FlowTest].
 */
abstract class ChainTest : FlowTest() {
    @Inject
    lateinit var startup: AppStartup

    @Inject
    lateinit var source: CalendarSource

    @Inject
    lateinit var scheduler: ReminderScheduler

    private val processes = mutableListOf<CoroutineScope>()

    protected val notifications: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    /** What the application does when the process starts: the same start-up the app uses. */
    protected fun startProcess(): CoroutineScope {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        processes += scope
        startup.start(scope)
        return scope
    }

    /** The process dies: nothing follows the calendar any more (the alarms stay in the system). */
    protected fun killProcess(scope: CoroutineScope) {
        scope.cancel()
        processes -= scope
    }

    @After
    fun stopProcesses() {
        processes.forEach { it.cancel() }
        processes.clear()
        // Alarms outlive the process, and the events are about to go: take the plan's back.
        scheduler.schedule(emptyList(), alarmClock = false)
    }

    /** The moment the app's alarms of the system fire at, as `dumpsys alarm` lists them. */
    protected fun alarmTimes(): List<Long> = AppAlarms.times(context.packageName)

    /** A whole minute [minutes] from now, so the alarm of a reminder lands on a minute too. */
    protected fun inMinutes(minutes: Long): ZonedDateTime =
        ZonedDateTime.now(calendars.zone).plusMinutes(minutes).truncatedTo(ChronoUnit.MINUTES)

    protected fun hasAlarmAt(millis: Long): Boolean =
        alarmTimes().any { abs(it - millis) <= ALARM_TOLERANCE_MS }

    /** Waits for an alarm at [millis]; the failure lists what the system had for the app. */
    protected fun waitForAlarmAt(millis: Long, timeoutMillis: Long = TIMEOUT_MS) {
        try {
            waitUntil(timeoutMillis) { hasAlarmAt(millis) }
        } catch (e: AssertionError) {
            throw AssertionError(
                "no alarm at $millis. ${AppAlarms.describe(context.packageName)}",
                e
            )
        }
    }

    protected fun notificationTitles(): List<String> =
        notifications.activeNotifications.mapNotNull {
            it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        }

    protected companion object {
        const val ALARM_TOLERANCE_MS = 60_000L
        const val SLOW_TIMEOUT_MS = 240_000L
    }
}

/** The alarms of the system for one package, read from `dumpsys alarm` (needs the shell's rights). */
internal object AppAlarms {
    private val alarm = Regex("""Alarm\{[^}]*\}""")
    private val whenMillis = Regex("""(?:origWhen|when) (\d{12,})""")

    private fun dump(): String {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        return ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("dumpsys alarm")
        ).use { it.readBytes().decodeToString() }
    }

    fun times(packageName: String): List<Long> = alarm.findAll(dump())
        .map { it.value }
        .filter { packageName in it }
        .mapNotNull { whenMillis.find(it)?.groupValues?.get(1)?.toLongOrNull() }
        .toList()

    /** For a failure message: the lines of the dump that mention the package. */
    fun describe(packageName: String): String = dump().lines()
        .filter { packageName in it }
        .joinToString("\n", prefix = "dumpsys alarm lines of the app:\n")
        .take(DESCRIBE_CHARS)

    private const val DESCRIBE_CHARS = 4_000
}
