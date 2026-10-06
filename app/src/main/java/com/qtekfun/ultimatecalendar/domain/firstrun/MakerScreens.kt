// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

/** A screen of another app, by package and class. */
data class Screen(val packageName: String, val className: String)

/**
 * The makers' own screens where the user allows auto-start, newest first (dontkillmyapp.com).
 * No app can switch those on by itself, and recent systems even refuse to open the screens
 * (ColorOS 16 asks for a permission only OPPO's apps have): the caller tries them in order and
 * falls back to the app's info page, where the same switches live.
 */
object MakerScreens {
    fun of(maker: PhoneMaker): List<Screen> = when (maker) {
        PhoneMaker.COLOROS -> listOf(
            Screen("com.oplus.battery", "com.oplus.startupapp.view.StartupAppListActivity"),
            Screen(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            Screen(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            Screen("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
        )

        PhoneMaker.VIVO -> listOf(
            Screen(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            Screen("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            Screen("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
        )

        PhoneMaker.XIAOMI -> listOf(
            Screen(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        )

        PhoneMaker.HUAWEI -> listOf(
            Screen(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            Screen(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        )

        // Samsung's battery switches are on the app's info page already.
        PhoneMaker.SAMSUNG, PhoneMaker.OTHER -> emptyList()
    }
}
