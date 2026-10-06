// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.firstrun

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import com.qtekfun.ultimatecalendar.domain.firstrun.MakerScreens
import com.qtekfun.ultimatecalendar.domain.firstrun.PhoneMaker

/** Screens of the system and of other apps where only the user can change a setting. */
object PhoneSettings {
    /** The app's notification settings, for when the system no longer shows its dialog. */
    fun openNotificationSettings(context: Context) = tryOpen(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )

    /** The app's page in the system settings, where makers put autostart and background options. */
    fun openAppSettings(context: Context, packageName: String = context.packageName) = tryOpen(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
    )

    /** Where another app's notifications are switched off, to stop its duplicate reminders. */
    fun openOtherAppNotifications(context: Context, packageName: String) {
        val opened = tryOpen(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
        if (!opened) openAppSettings(context, packageName)
    }

    /** The system's accounts screen, where Google or DAVx5 accounts are added. */
    fun openAccounts(context: Context) {
        val opened = tryOpen(context, Intent(Settings.ACTION_SYNC_SETTINGS))
        if (!opened) tryOpen(context, Intent(Settings.ACTION_SETTINGS))
    }

    /**
     * The maker's own auto-start screen when the system lets apps open it, otherwise the app's
     * info page, where recent systems keep the same switches.
     */
    fun openMakerSettings(context: Context, maker: PhoneMaker) {
        val opened = MakerScreens.of(maker).any { screen ->
            tryOpen(
                context,
                Intent().setClassName(screen.packageName, screen.className)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        if (!opened) openAppSettings(context)
    }

    /** False when the screen is missing or, as on recent ColorOS, closed to other apps. */
    fun tryOpen(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
