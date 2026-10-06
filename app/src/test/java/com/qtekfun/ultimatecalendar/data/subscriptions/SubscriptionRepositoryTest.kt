// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.settings.backup.BackupSubscription
import com.qtekfun.ultimatecalendar.data.source.SubscriptionIds
import com.qtekfun.ultimatecalendar.domain.editor.EventColorChoice
import com.qtekfun.ultimatecalendar.domain.subscriptions.RefreshInterval
import com.qtekfun.ultimatecalendar.domain.subscriptions.SchedulePlan
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionRepositoryTest {
    @StartStop
    val server = MockWebServer()

    private val rig by lazy { SubscriptionRig(server) }
    private val repository get() = rig.repository
    private val blue = EventColorChoice.PEACOCK.argb
    private val secretUrl = "https://cal.example.com/private/abc123/basic.ics?token=s3cr3t"

    @AfterEach
    fun close() = rig.close()

    @Test
    fun `a webcal address is added as https, named after the host when there is no name`() =
        runTest {
            val result = repository.add(
                "webcal://cal.example.com/private/abc123/basic.ics?token=s3cr3t",
                "  ",
                blue,
                RefreshInterval.EVERY_6_HOURS
            )

            val added = (result as AddResult.Added).id
            val subscription = repository.all().single()
            assertEquals(added, subscription.id)
            assertEquals("cal.example.com", subscription.name)
            assertEquals("cal.example.com", subscription.host)
            assertEquals(blue, subscription.color)
            assertEquals(RefreshInterval.EVERY_6_HOURS, subscription.interval)
            assertTrue(subscription.enabled)
            assertEquals(listOf(SchedulePlan.Every(6)), rig.scheduler.plans)
        }

    @Test
    fun `the address is stored encrypted and only the host can be read back from the row`() =
        runTest {
            repository.add(secretUrl, "Work", blue, RefreshInterval.DEFAULT)

            val row = rig.subscriptions.all().single()

            assertEquals(secretUrl, rig.vault.open(row.urlSecret))
            val everything = row.toString()
            assertFalse("s3cr3t" in everything)
            assertFalse("abc123" in everything)
            assertEquals(rig.vault.keyOf(secretUrl), row.urlKey)
        }

    @Test
    fun `plain http, nonsense and a repeated address are refused and nothing is scheduled`() =
        runTest {
            repository.add(secretUrl, "One", blue, RefreshInterval.DEFAULT)
            rig.scheduler.plans.clear()

            assertEquals(
                AddResult.Insecure,
                repository.add("http://cal.example.com/a.ics", "", blue, RefreshInterval.DEFAULT)
            )
            assertEquals(
                AddResult.Invalid,
                repository.add("not a url", "", blue, RefreshInterval.DEFAULT)
            )
            assertEquals(
                AddResult.Duplicate,
                repository.add(
                    "webcal://cal.example.com/private/abc123/basic.ics?token=s3cr3t",
                    "Again",
                    blue,
                    RefreshInterval.DEFAULT
                )
            )
            assertEquals(1, repository.all().size)
            assertEquals(emptyList<SchedulePlan>(), rig.scheduler.plans)
        }

    @Test
    fun `renaming, switching off and changing the interval keep the schedule in step`() = runTest {
        val id = (
            repository.add(secretUrl, "Work", blue, RefreshInterval.EVERY_24_HOURS)
                as AddResult.Added
            ).id

        repository.rename(id, "  Team  ", EventColorChoice.GRAPE.argb)
        repository.setInterval(id, RefreshInterval.EVERY_6_HOURS)
        repository.setEnabled(id, false)
        repository.setEnabled(id, true)
        repository.setInterval(id, RefreshInterval.MANUAL)

        val subscription = repository.all().single()
        assertEquals("Team", subscription.name)
        assertEquals(EventColorChoice.GRAPE.argb, subscription.color)
        assertEquals(RefreshInterval.MANUAL, subscription.interval)
        assertEquals(
            listOf(
                SchedulePlan.Every(24),
                SchedulePlan.Every(24),
                SchedulePlan.Every(6),
                SchedulePlan.None,
                SchedulePlan.Every(6),
                SchedulePlan.None
            ),
            rig.scheduler.plans
        )
    }

    @Test
    fun `a blank name goes back to the host`() = runTest {
        val id = (
            repository.add(secretUrl, "Work", blue, RefreshInterval.DEFAULT)
                as AddResult.Added
            ).id

        repository.rename(id, "", blue)

        assertEquals("cal.example.com", repository.all().single().name)
    }

    @Test
    fun `changing one that does not exist changes nothing`() = runTest {
        repository.setEnabled(42, false)
        repository.rename(42, "x", blue)

        assertEquals(emptyList<Any>(), repository.all())
    }

    @Test
    fun `removing the last one cancels the periodic work and clears its local settings`() =
        runTest {
            val id = (
                repository.add(secretUrl, "Work", blue, RefreshInterval.DEFAULT)
                    as AddResult.Added
                ).id
            val calendar = SubscriptionIds.calendar(id).value
            rig.db.calendarSettingsDao().save(CalendarSettingsEntity(calendar, null, null, false))
            rig.scheduler.plans.clear()

            repository.remove(id)

            assertEquals(emptyList<Any>(), repository.all())
            assertNull(rig.db.calendarSettingsDao().find(calendar))
            assertEquals(listOf(SchedulePlan.None), rig.scheduler.plans)
        }

    @Test
    fun `the list follows the changes`() = runTest {
        repository.subscriptions.test {
            assertEquals(emptyList<Any>(), awaitItem())
            repository.add(secretUrl, "Work", blue, RefreshInterval.DEFAULT)
            assertEquals(listOf("Work"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the backup gets the addresses, and what cannot be decrypted is left out`() = runTest {
        repository.add(secretUrl, "Work", blue, RefreshInterval.EVERY_24_HOURS)
        repository.add("https://other.example.com/b.ics", "Other", blue, RefreshInterval.MANUAL)
        val broken = rig.subscriptions.byUrlKey(
            rig.vault.keyOf("https://other.example.com/b.ics")
        )!!
        rig.subscriptions.update(broken.copy(urlSecret = "garbage"))

        assertEquals(
            listOf(BackupSubscription("Work", secretUrl, blue, true, 24)),
            repository.forBackup()
        )
    }

    @Test
    fun `a restore checks every entry as if the user typed it`() = runTest {
        repository.add("https://have.example.com/a.ics", "Have", blue, RefreshInterval.DEFAULT)
        rig.scheduler.plans.clear()

        val result = repository.restore(
            listOf(
                BackupSubscription("Good", "webcal://good.example.com/a.ics", 123, false, 6),
                BackupSubscription("Plain", "http://plain.example.com/a.ics"),
                BackupSubscription("Junk", "not a url"),
                BackupSubscription("Same", "https://have.example.com/a.ics"),
                BackupSubscription("  ", "https://defaults.example.com/a.ics", null, null, 7)
            )
        )

        assertEquals(RestoredSubscriptions(added = 2, refused = 2), result)
        val all = repository.all().associateBy { it.host }
        assertEquals(
            setOf("have.example.com", "good.example.com", "defaults.example.com"),
            all.keys
        )
        val good = all.getValue("good.example.com")
        assertEquals("Good", good.name)
        assertEquals(123, good.color)
        assertFalse(good.enabled)
        assertEquals(RefreshInterval.EVERY_6_HOURS, good.interval)
        val defaults = all.getValue("defaults.example.com")
        assertEquals("defaults.example.com", defaults.name)
        assertEquals(blue, defaults.color)
        assertTrue(defaults.enabled)
        assertEquals(RefreshInterval.DEFAULT, defaults.interval)
        assertEquals(1, rig.scheduler.refreshes)
        assertEquals(SchedulePlan.Every(12), rig.scheduler.plans.last())
    }

    @Test
    fun `restoring nothing new does not ask for a download`() = runTest {
        val result = repository.restore(
            listOf(BackupSubscription("Plain", "http://x.example.com/a"))
        )

        assertEquals(RestoredSubscriptions(0, 1), result)
        assertEquals(0, rig.scheduler.refreshes)
    }

    @Test
    fun `rescheduling follows what is stored`() = runTest {
        repository.reschedule()
        repository.add(secretUrl, "Work", blue, RefreshInterval.EVERY_6_HOURS)
        rig.scheduler.plans.clear()
        repository.reschedule()

        assertEquals(listOf<SchedulePlan>(SchedulePlan.Every(6)), rig.scheduler.plans)
    }
}
