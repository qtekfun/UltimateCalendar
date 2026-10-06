// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar

import android.Manifest
import android.app.LocaleManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import com.qtekfun.ultimatecalendar.di.TimeModule
import com.qtekfun.ultimatecalendar.domain.firstrun.FirstRunFlag
import com.qtekfun.ultimatecalendar.domain.settings.FirstDayOfWeek
import com.qtekfun.ultimatecalendar.screenshots.AppDriver
import com.qtekfun.ultimatecalendar.screenshots.DemoProvider
import com.qtekfun.ultimatecalendar.screenshots.DemoWeek
import com.qtekfun.ultimatecalendar.screenshots.DeviceTools
import com.qtekfun.ultimatecalendar.screenshots.ScreenshotFiles
import com.qtekfun.ultimatecalendar.sync.InvitationCheckOutcome
import com.qtekfun.ultimatecalendar.sync.InvitationChecker
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Store and README screenshots: invented calendars and events in a LOCAL test account of the real
 * calendar provider, a fixed clock and a fixed week, so every run draws the same images, in
 * English and Spanish. It only writes images, so it is skipped unless asked for (`-e screenshots
 * true`) and must never run on a personal phone: `tools/take-screenshots.sh` runs it on an
 * emulator and copies the PNGs to `fastlane/metadata/android/<locale>/images/`.
 *
 * Needs Android 13 or later (per-app languages).
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
@HiltAndroidTest
@UninstallModules(TimeModule::class)
@RunWith(AndroidJUnit4::class)
class Screenshots {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val zone: ZoneId = ZoneId.systemDefault()

    /** Monday, 9:05: the whole invented week is still ahead, so its invitations are pending. */
    @BindValue
    @JvmField
    val clock: Clock = Clock.fixed(NOW.atZone(zone).toInstant(), zone)

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var firstRun: FirstRunFlag

    @Inject
    lateinit var checker: InvitationChecker

    @Inject
    lateinit var notified: NotifiedInvitations

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ScreenshotFiles(context)
    private val provider = DemoProvider(context, zone)
    private var armed = false

    @Before
    fun setUp() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("screenshots") == "true")
        hilt.inject()
        armed = true
        grant(Manifest.permission.READ_CALENDAR)
        grant(Manifest.permission.WRITE_CALENDAR)
        grant(Manifest.permission.POST_NOTIFICATIONS)
        // No first-run wizard in the pictures.
        firstRun.markDone()
    }

    @After
    fun tearDown() {
        if (!armed) return
        provider.clear()
        settings.restore(AppSettings())
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.getEmptyLocaleList()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        runBlocking { notified.replaceAll(emptyList()) }
    }

    @Test
    fun phoneScreenshots() {
        assumeTrue("a phone", !isTablet())
        LOCALES.forEach { capturePhone(it) }
    }

    private fun capturePhone(locale: StoreLocale) {
        files.reset(locale.store, PHONE)
        val week = prepare(locale)
        val driver = AppDriver(compose, context, locale.locale)
        DeviceTools.enterDemoMode(DEMO_CLOCK)
        try {
            invitationNotification(locale)
            provider.add(week.secondInvitation)
            driver.launch()
            lightViews(locale, week, driver)
            darkDay(locale, driver)
        } catch (failure: Throwable) {
            // What the screen showed when it went wrong, for whoever reads the artifact.
            snap(locale, "failure", DeviceTools.screen())
            throw failure
        } finally {
            driver.close()
            DeviceTools.shell("cmd uimode night auto")
            DeviceTools.exitDemoMode()
        }
    }

    private fun lightViews(locale: StoreLocale, week: DemoWeek, driver: AppDriver) {
        driver.switchTo(R.string.shell_view_agenda)
        driver.waitForText(week.earlyTitle)
        snap(locale, "1_agenda", driver.capture())
        driver.switchTo(R.string.shell_view_week)
        driver.waitForText(week.earlyTitle)
        snap(locale, "2_week", driver.capture())
        driver.switchTo(R.string.shell_view_month)
        driver.waitForText(week.earlyTitle)
        snap(locale, "3_month", driver.capture())
        driver.switchTo(R.string.shell_view_agenda)
        driver.openEvent(week.detailTitle)
        driver.waitForText("Liam Novak")
        snap(locale, "4_event_detail", driver.capture())
        driver.editOpenEvent()
        snap(locale, "8_event_editor", driver.capture())
        driver.back()
        driver.openInvitations(pending = 2)
        driver.waitForText(week.secondInvitationTitle)
        snap(locale, "5_invitations", driver.capture())
        driver.back()
    }

    private fun darkDay(locale: StoreLocale, driver: AppDriver) {
        settings.update { it.copy(theme = ThemeMode.DARK) }
        // The system bar icons follow the system theme, so the device goes dark too.
        DeviceTools.shell("cmd uimode night yes")
        driver.switchTo(R.string.shell_view_day)
        driver.waitForText(driver.string(R.string.shell_view_day))
        snap(locale, "7_day_dark", driver.capture())
    }

    /**
     * The notification of a pending invitation with its Accept, Maybe and Decline buttons, taken
     * with the real notifier and the shade pulled down. One invitation only, so the system shows
     * it open instead of folded into a group.
     */
    private fun invitationNotification(locale: StoreLocale) {
        runBlocking {
            notified.replaceAll(emptyList())
            val outcome = checker.check(requestSync = false)
            check(outcome is InvitationCheckOutcome.Done && outcome.pending == 1) {
                "check: $outcome"
            }
        }
        val shown = context.getSystemService(NotificationManager::class.java).activeNotifications
        check(shown.isNotEmpty()) {
            "no notification was posted: " + DeviceTools.shell("dumpsys notification --noredact")
                .lineSequence().filter {
                    context.packageName in it
                }.take(DUMP_LINES).joinToString("\n")
        }
        try {
            DeviceTools.openShade()
            Thread.sleep(SHADE_MS)
            snap(locale, "6_invitation_notification", notificationCard(DeviceTools.screen()))
        } finally {
            DeviceTools.closeShade()
            context.getSystemService(NotificationManager::class.java).cancelAll()
            runBlocking { notified.replaceAll(emptyList()) }
        }
    }

    /**
     * Only the card of the notification: the shade around it shows the emulator's own notices and
     * today's real date.
     */
    private fun notificationCard(screen: Bitmap): Bitmap = Bitmap.createBitmap(
        screen,
        0,
        (screen.height * CARD_TOP).toInt(),
        screen.width,
        (screen.height * CARD_HEIGHT).toInt()
    )

    /** Sets the app language and the demo data; returns the week that was loaded. */
    private fun prepare(locale: StoreLocale): DemoWeek {
        useLanguage(locale)
        settings.restore(
            AppSettings(
                theme = ThemeMode.LIGHT,
                dynamicColor = false,
                firstDayOfWeek = FirstDayOfWeek.MONDAY
            )
        )
        val demo = DemoWeek(NOW.toLocalDate(), locale.spanish)
        provider.createCalendars(demo.calendars)
        demo.events.forEach(provider::add)
        return demo
    }

    private fun useLanguage(locale: StoreLocale) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(locale.tag)
        val deadline = System.currentTimeMillis() + LOCALE_TIMEOUT_MS
        while (context.resources.configuration.locales[0].language != locale.locale.language &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(POLL_MS)
        }
    }

    private fun snap(locale: StoreLocale, name: String, image: Bitmap) =
        files.save(locale.store, PHONE, name, image)

    private fun isTablet() = context.resources.configuration.smallestScreenWidthDp >= TABLET_DP

    private fun grant(permission: String) {
        val output = DeviceTools.shell("pm grant ${context.packageName} $permission")
        check(context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            "could not grant $permission: $output"
        }
    }

    /** A language of the store listing and the tag the app is switched to. */
    private data class StoreLocale(val store: String, val tag: String) {
        val locale: Locale = Locale.forLanguageTag(tag)
        val spanish get() = locale.language == "es"
    }

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 12, 9, 5)
        val LOCALES = listOf(StoreLocale("en-US", "en-US"), StoreLocale("es-ES", "es-ES"))
        const val PHONE = "phoneScreenshots"
        const val DEMO_CLOCK = "0905"
        const val SHADE_MS = 2_500L
        const val LOCALE_TIMEOUT_MS = 10_000L
        const val POLL_MS = 100L
        const val CARD_TOP = 0.215f
        const val CARD_HEIGHT = 0.235f
        const val DUMP_LINES = 20
        const val TABLET_DP = 600
    }
}
