// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.flows

import android.app.NotificationManager
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatecalendar.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * What every key-flow test shares: the Hilt app, a Compose rule that is not tied to an activity
 * (the activity is launched by the test, after its events are seeded), the LOCAL test calendars
 * of [LocalCalendars], and the few UI steps all flows repeat. Run with `adb shell am instrument`
 * on an emulator (CI does), never on a personal phone: the tests only touch their own account but
 * the app they start shows every calendar on the device.
 *
 * Waiting is done with [waitUntil] (Compose's own idling plus a condition), never with sleeps; the
 * emulator runs with animations off.
 */
abstract class FlowTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    protected val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    protected lateinit var calendars: LocalCalendars
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Whether the first-run wizard is already done; the tests of the wizard itself say no. */
    protected open val wizardDone: Boolean = true

    @Before
    fun prepareFlow() {
        // The flag lives in the settings; the old preferences file it was migrated from is cleared
        // so that a stale value cannot be migrated over it.
        context.getSharedPreferences("first_run", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("first_run_done", wizardDone).commit()
        hilt.inject()
        calendars = LocalCalendars(context).also { it.setUp() }
    }

    @After
    fun cleanUpFlow() {
        scenario?.close()
        scenario = null
        calendars.tearDown()
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    protected fun launchApp() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    protected fun closeApp() {
        scenario?.close()
        scenario = null
    }

    /** Waits (up to [timeoutMillis]) for [condition], which may look at the UI or the provider. */
    protected fun waitUntil(timeoutMillis: Long = TIMEOUT_MS, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMillis, condition)
        } catch (e: ComposeTimeoutException) {
            // A timeout alone says nothing about why: put what was on screen in the report.
            throw AssertionError("${e.message}\n${describeScreen()}", e)
        }
    }

    private fun describeScreen(): String {
        val roots = compose.onAllNodes(isRoot())
        val tree = runCatching {
            (0 until roots.fetchSemanticsNodes().size).joinToString("\n---\n") {
                roots[it].printToString(Int.MAX_VALUE)
            }
        }.getOrElse { "no semantics tree: $it" }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val windows = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("dumpsys window windows")
        ).use { stream -> stream.readBytes().decodeToString() }
            .lines().filter { "mCurrentFocus" in it || "mFocusedApp" in it }
        return (windows + tree).joinToString("\n").take(SCREEN_DUMP_CHARS)
    }

    protected fun click(text: String) {
        waitForText(text)
        compose.onNodeWithText(text).performClick()
    }

    /** Clicks the node described [description]; [scroll] first brings it into view in a scroller. */
    protected fun clickDescribed(
        description: String,
        substring: Boolean = false,
        scroll: Boolean = false
    ) {
        val matcher = hasContentDescription(description, substring = substring)
        waitUntil { compose.onAllNodes(matcher).count() > 0 }
        val node = compose.onNode(matcher)
        if (scroll) node.performScrollTo()
        node.performClick()
    }

    protected fun waitForDescribed(description: String) =
        waitUntil { compose.onAllNodes(hasContentDescription(description)).count() > 0 }

    protected fun waitForText(text: String) =
        waitUntil { compose.onAllNodesWithText(text).count() > 0 }

    /** The shell opens on the Week view: switches to the Agenda, which lists events as rows. */
    protected fun showAgenda() {
        clickDescribed("Change view, now Week")
        // The view menu is a popup; wide windows also show the views as tabs, with the same text.
        val item = hasText("Agenda") and hasAnyAncestor(isPopup())
        waitUntil { compose.onAllNodes(item).count() > 0 }
        compose.onNode(item).performClick()
    }

    /** The agenda rows of the events titled [title] (their description starts with the title). */
    protected fun rows(title: String): SemanticsNodeInteractionCollection =
        compose.onAllNodes(hasContentDescription(title, substring = true))

    protected fun waitForRows(title: String, count: Int) =
        waitUntil { rows(title).count() == count }

    /** The [index]th agenda row of [title], soonest first. */
    protected fun row(title: String, index: Int = 0): SemanticsNodeInteraction {
        waitUntil { rows(title).count() > index }
        return rows(title)[index]
    }

    private fun SemanticsNodeInteractionCollection.count(): Int = fetchSemanticsNodes().size

    protected companion object {
        const val TIMEOUT_MS = 20_000L
        const val SCREEN_DUMP_CHARS = 8_000
    }
}
