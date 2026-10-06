// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatecalendar.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

@HiltAndroidTest
class StartupTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** A first run, whatever the device has seen before. */
    @get:Rule(order = 1)
    val firstRun = object : ExternalResource() {
        override fun before() {
            InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("first_run", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstRunShowsTheWizardThenTheApp() {
        compose.onNodeWithText("Set up UltimateCalendar").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("UltimateCalendar").assertIsDisplayed()
    }
}
