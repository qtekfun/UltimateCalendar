// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.screenshots

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.ui.MainActivity
import java.util.Locale

/**
 * Drives the running app by what a person sees: the view switcher, the event rows, the tray. It
 * finds everything by its text or description in the language of the run ([l10n]), so it works
 * the same in English and in Spanish.
 */
class AppDriver(private val compose: ComposeTestRule, base: Context, locale: Locale) {
    private val l10n: Context = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply { setLocale(locale) }
    )
    private var scenario: ActivityScenario<MainActivity>? = null

    fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    fun close() {
        scenario?.close()
        scenario = null
    }

    /** The system back button or gesture. */
    fun back() {
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /**
     * Switches the view with the drawer (the switcher of the top bar opens a popup the test
     * cannot click reliably); [label] is the view's string.
     */
    fun switchTo(label: Int) {
        compose.onNodeWithContentDescription(l10n.getString(R.string.shell_open_drawer))
            .performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText(l10n.getString(label)) and hasClickAction()).onFirst()
            .performClick()
        compose.waitForIdle()
    }

    fun openInvitations(pending: Int) {
        val description =
            l10n.resources.getQuantityString(R.plurals.shell_invitations_pending, pending, pending)
        waitForDescription(description)
        compose.onNodeWithContentDescription(description).performClick()
        compose.waitForIdle()
    }

    /** Opens the detail of the event with [title], scrolling the list to it if needed. */
    fun openEvent(title: String) {
        waitForText(title)
        runCatching {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(title))
        }
        compose.onAllNodesWithText(title).onFirst().performClick()
        compose.waitForIdle()
    }

    fun string(id: Int): String = l10n.getString(id)

    fun waitForText(text: String) = waitUntil { has(hasText(text)) }

    private fun waitForDescription(description: String) =
        waitUntil { has(hasContentDescription(description)) }

    private fun has(matcher: SemanticsMatcher) =
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun waitUntil(condition: () -> Boolean) {
        compose.waitUntil(TIMEOUT_MS, condition)
        compose.waitForIdle()
    }

    /** The app's own content, without the system bars. */
    fun capture(): Bitmap {
        compose.waitForIdle()
        return compose.onRoot().captureToImage().asAndroidBitmap()
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
