// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.firstrun

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.qtekfun.ultimatecalendar.domain.firstrun.CalendarPresence
import com.qtekfun.ultimatecalendar.domain.firstrun.InstalledPackages
import com.qtekfun.ultimatecalendar.domain.firstrun.OtherCalendarApps
import com.qtekfun.ultimatecalendar.domain.firstrun.PhoneMaker
import com.qtekfun.ultimatecalendar.domain.firstrun.SetupStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Reads what the system allows right now, for the wizard (RF-01). */
class SystemSetup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendars: CalendarPresence
) {
    suspend fun status(): SetupStatus {
        val permission = ReminderPermissions.calendarGranted(context)
        return SetupStatus(
            calendarPermission = permission,
            notifications = ReminderPermissions.notificationsAllowed(context),
            exactAlarms = ReminderPermissions.exactAlarmsAllowed(context),
            batteryExempt = ReminderPermissions.batteryExempt(context),
            hasCalendars = if (permission) calendars.hasCalendars() else null,
            maker = PhoneMaker.of(Build.MANUFACTURER),
            otherCalendarApps = OtherCalendarApps(launchable(context.packageManager)).installed()
        )
    }

    /** An app with a launcher entry is installed and enabled; `<queries>` makes it visible. */
    private fun launchable(packages: PackageManager) =
        InstalledPackages { packages.getLaunchIntentForPackage(it) != null }

    /** The name the phone shows for [packageName], or the package itself if it has none. */
    fun label(packageName: String): String =
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?.resolveActivityInfo(context.packageManager, 0)
            ?.loadLabel(context.packageManager)?.toString() ?: packageName
}
