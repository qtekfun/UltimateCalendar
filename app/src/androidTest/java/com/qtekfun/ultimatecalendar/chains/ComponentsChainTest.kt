// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.chains

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.qtekfun.ultimatecalendar.data.widget.WidgetDataLoader
import com.qtekfun.ultimatecalendar.data.widget.WidgetLoad
import com.qtekfun.ultimatecalendar.domain.widget.AgendaWidgetRow
import com.qtekfun.ultimatecalendar.domain.widget.WidgetPalette
import com.qtekfun.ultimatecalendar.widget.AgendaWidgetViews
import com.qtekfun.ultimatecalendar.widget.MonthWidgetViews
import com.qtekfun.ultimatecalendar.widget.WidgetFormats
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RF-08 and RF-12 entry points: what the manifest declares exists and is exported only where it
 * must be, the widgets are installed and inflate with the real events of the provider, and the
 * boot broadcast reaches the receiver.
 */
@HiltAndroidTest
class ComponentsChainTest : ChainTest() {
    @Inject
    lateinit var loader: WidgetDataLoader

    private val packages: PackageManager get() = context.packageManager

    @Test
    fun everyComponentOfTheManifestLoadsAndOnlyTheRightOnesAreExported() {
        @Suppress("DEPRECATION")
        val info = packages.getPackageInfo(
            context.packageName,
            PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or
                PackageManager.GET_PROVIDERS or PackageManager.GET_ACTIVITIES
        )
        val components = listOfNotNull(
            info.receivers,
            info.services,
            info.providers,
            info.activities
        ).flatMap { it.toList() }
        val own = components.filter { it.name.startsWith(context.packageName) }
        own.forEach { Class.forName(it.name) }

        val exported = own.filter { it.exported }.map { it.name.substringAfterLast('.') }
        // The debug build also has its demonstration screen; nothing else may be open.
        val allowed = setOf(
            "MainActivity",
            "BootReceiver",
            "AgendaWidgetProvider",
            "MonthWidgetProvider",
            "DemoActivity"
        )
        assertEquals("exported: $exported", emptySet<String>(), exported.toSet() - allowed)
        assertTrue(exported.containsAll(listOf("MainActivity", "BootReceiver")))
    }

    @Test
    fun theBootBroadcastsReachTheReceiver() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        ).forEach { action ->
            val receivers = packages.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName),
                0
            ).map { it.activityInfo.name.substringAfterLast('.') }
            assertTrue("$action: $receivers", "BootReceiver" in receivers)
        }
    }

    @Test
    fun bothWidgetsAreInstalledAndTheirLayoutsInflate() {
        val providers = AppWidgetManager.getInstance(context).installedProviders
            .filter { it.provider.packageName == context.packageName }
        assertEquals(
            setOf("AgendaWidgetProvider", "MonthWidgetProvider"),
            providers.map { it.provider.className.substringAfterLast('.') }.toSet()
        )
        providers.forEach {
            val loading = LayoutInflater.from(context)
                .inflate(it.initialLayout, FrameLayout(context), false)
            assertNotNull(loading)
        }
    }

    @Test
    fun theWidgetsShowTheRealEventsOfTheProvider() {
        val title = calendars.unique("Widget")
        // Half an hour ahead: still to come for the widget, which hides what already ended today.
        val start = inMinutes(MINUTES_AHEAD)
        calendars.seed(calendars.timed(title, calendars.work, start))
        val today = LocalDate.now(calendars.zone)
        val palette = WidgetPalette.of(dark = false, amoled = false, tones = null)
        val formats = WidgetFormats(context, calendars.zone)

        val agenda = (runBlocking { loader.agenda() } as WidgetLoad.Loaded).value
        val titles = agenda.rows.filterIsInstance<AgendaWidgetRow.Event>()
            .map { it.entry.instance.title }
        assertTrue("agenda rows: $titles", title in titles)
        AgendaWidgetViews.frame(context, 1, today, palette, formats)
            .apply(context, FrameLayout(context))

        val month = (runBlocking { loader.month(0, MARKERS) } as WidgetLoad.Loaded).value
        val events = month.weeks.flatten().sumOf { it.eventCount }
        assertTrue("events in the month widget: $events", events >= 1)
        MonthWidgetViews.grid(context, 1, month, palette, formats)
            .apply(context, FrameLayout(context))
    }

    private companion object {
        const val MINUTES_AHEAD = 30L
        const val MARKERS = 3
    }
}
