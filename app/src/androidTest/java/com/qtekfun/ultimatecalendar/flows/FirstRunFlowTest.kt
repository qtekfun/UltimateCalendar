// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.flows

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/** Flow 7: the first-run wizard shows once; after Done a new start goes straight to the app. */
@HiltAndroidTest
class FirstRunFlowTest : FlowTest() {
    override val wizardDone: Boolean = false

    @Test
    fun wizardDoesNotComeBackAfterDone() {
        launchApp()
        compose.onNodeWithText("Set up UltimateCalendar").assertIsDisplayed()
        click("Done", scroll = true)
        waitForDescribed("Create")

        // A fresh start of the app, as when the user opens it the next day.
        closeApp()
        launchApp()
        waitForDescribed("Create")
        compose.onNodeWithText("Set up UltimateCalendar").assertDoesNotExist()
    }
}
