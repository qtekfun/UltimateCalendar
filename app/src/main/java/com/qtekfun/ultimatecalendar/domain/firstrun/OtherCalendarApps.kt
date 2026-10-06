// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

/** Whether an app is installed (and enabled) on the phone. */
fun interface InstalledPackages {
    fun isInstalled(packageName: String): Boolean
}

/**
 * Finds other calendar apps that raise their own reminders for the same events, which would then
 * ring twice (RF-07). Only the apps in [KNOWN] are looked for: Android hides the rest, and the
 * manifest's `<queries>` lists exactly these (a test keeps both in step).
 */
class OtherCalendarApps(private val installed: InstalledPackages) {
    fun installed(): List<String> = KNOWN.filter(installed::isInstalled)

    companion object {
        val KNOWN: List<String> = listOf(
            "com.google.android.calendar",
            "com.samsung.android.calendar",
            "com.android.calendar",
            "com.coloros.calendar",
            "com.huawei.calendar",
            "com.microsoft.office.outlook",
            "ws.xsoh.etar",
            "org.fossify.calendar",
            "com.simplemobiletools.calendar.pro",
            "me.proton.android.calendar",
            "com.appgenix.bizcal",
            "org.withouthat.acalendar"
        )
    }
}
