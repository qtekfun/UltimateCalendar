// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Shell commands run as the shell user through UiAutomation (grants, status bar, shade). */
object DeviceTools {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    /** Runs [command] and returns its output; the command has finished when this returns. */
    fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)
    ).use { it.readBytes().decodeToString() }

    /** The whole screen, system bars and shade included, as the system shows it. */
    fun screen(): Bitmap = requireNotNull(automation.takeScreenshot()) { "no screenshot" }

    /**
     * A status bar without the device's own details: fixed clock, full battery, no notification
     * icons, so the images are the same on every run and show nothing about the device.
     */
    fun enterDemoMode(hhmm: String) {
        shell("settings put global sysui_demo_allowed 1")
        demo("enter")
        demo("clock", "hhmm" to hhmm)
        demo("battery", "level" to "100", "plugged" to "false")
        demo("network", "wifi" to "show", "level" to "4", "fully" to "true")
        demo("network", "mobile" to "hide")
        demo("notifications", "visible" to "false")
    }

    fun exitDemoMode() {
        demo("exit")
    }

    private fun demo(command: String, vararg extras: Pair<String, String>) {
        val arguments = extras.joinToString(" ") { (key, value) -> "-e $key $value" }
        shell("am broadcast -a com.android.systemui.demo -e command $command $arguments")
    }

    fun openShade() {
        shell("cmd statusbar expand-notifications")
    }

    fun closeShade() {
        shell("cmd statusbar collapse")
    }
}

/** Where the images go: `<root>/<store locale>/images/<kind>/<name>.png`, like fastlane. */
class ScreenshotFiles(context: Context) {
    private val root = File(requireNotNull(context.getExternalFilesDir(null)), "screenshots")

    private fun folder(locale: String, kind: String) = File(root, "$locale/images/$kind")

    /** Empties one folder, so a run never mixes with an older one. */
    fun reset(locale: String, kind: String) {
        folder(locale, kind).deleteRecursively()
    }

    fun save(locale: String, kind: String, name: String, image: Bitmap) {
        val folder = folder(locale, kind).apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, QUALITY, it)
        }
    }

    private companion object {
        const val QUALITY = 100
    }
}
