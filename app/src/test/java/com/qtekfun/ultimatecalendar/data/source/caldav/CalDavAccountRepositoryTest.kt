// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.local.entity.NotifiedInvitationEntity
import com.qtekfun.ultimatecalendar.data.local.entity.PendingCalendarOverrideEntity
import com.qtekfun.ultimatecalendar.data.local.entity.PendingOperationEntity
import com.qtekfun.ultimatecalendar.data.local.entity.ReRemindEntity
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.local.model.OperationType
import com.qtekfun.ultimatecalendar.data.source.CalDavIds
import com.qtekfun.ultimatecalendar.domain.auth.Logout
import com.qtekfun.ultimatecalendar.domain.caldav.CalDavCalendarItem
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.sync.CalDavSync
import com.qtekfun.ultimatecalendar.sync.RecordingCalDavScheduler
import com.qtekfun.ultimatecalendar.sync.engine.InMemoryLastSyncStore
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import com.qtekfun.ultimatecalendar.sync.engine.SyncStatus
import com.qtekfun.ultimatecalendar.sync.queue.ProcessResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalDavAccountRepositoryTest {
    private val signedIn = SignedInAccount("https://cloud.example.com/", "ana")
    private val active = MutableStateFlow<SignedInAccount?>(null)
    private val session = mockk<AccountSession> { every { activeAccount } returns active }
    private val db = inMemoryDatabase()
    private val scheduler = RecordingCalDavScheduler()
    private val outcome = MutableStateFlow<SyncOutcome?>(null)
    private val syncing = MutableStateFlow(false)
    private val sync = mockk<CalDavSync> {
        every { lastOutcome } returns outcome
        every { this@mockk.syncing } returns this@CalDavAccountRepositoryTest.syncing
    }
    private val logout = mockk<Logout>(relaxed = true)
    private val lastSync = InMemoryLastSyncStore()
    private var removedCalls = 0
    private val repository = CalDavAccountRepository(
        session,
        db,
        sync,
        scheduler,
        logout,
        lastSync,
        { removedCalls++ },
        Dispatchers.IO
    )

    @AfterEach
    fun close() = db.close()

    private suspend fun account(server: SignedInAccount = signedIn): Long =
        db.davAccountDao().insert(
            DavAccountEntity(serverUrl = server.serverUrl, loginName = server.loginName)
        )

    private suspend fun calendar(
        accountId: Long,
        name: String,
        order: Int? = null,
        writable: Boolean = true,
        color: String? = "#336699"
    ): CalendarId = CalDavIds.calendar(
        db.davCalendarDao().insert(
            DavCalendarEntity(
                accountId = accountId,
                href = "/$name/",
                name = name,
                sortOrder = order,
                writable = writable,
                color = color
            )
        )
    )

    @Test
    fun `the state follows the session, the first sync and the queue`() = runBlocking {
        repository.state.test {
            assertEquals(CalDavAccountState.SignedOut, awaitItem())

            active.value = signedIn
            // Signed in, but nothing synced yet: no row.
            assertEquals(
                CalDavAccountState.SignedIn(signedIn, 0, emptyList(), false, 0, 0),
                awaitItem()
            )

            val id = db.davAccountDao().insert(
                DavAccountEntity(
                    serverUrl = signedIn.serverUrl,
                    loginName = "ana",
                    userAddresses = "ana@example.com,ana@work.example",
                    scheduling = true
                )
            )
            awaitItem()
            db.davCalendarDao().insert(DavCalendarEntity(accountId = id, href = "/a/", name = "A"))
            val queue = db.pendingOperationDao()
            queue.insert(
                PendingOperationEntity(
                    accountId = id,
                    type = OperationType.CREATE,
                    eventId = 1,
                    createdAt = 0
                )
            )
            queue.insert(
                PendingOperationEntity(
                    accountId = id,
                    type = OperationType.UPDATE,
                    eventId = 2,
                    createdAt = 0,
                    failed = true
                )
            )

            var latest = awaitItem()
            while (latest != CalDavAccountState.SignedIn(
                    signedIn,
                    1,
                    listOf("ana@example.com", "ana@work.example"),
                    true,
                    1,
                    1
                )
            ) {
                latest = awaitItem()
            }
            active.value = null
            var last = awaitItem()
            while (last != CalDavAccountState.SignedOut) last = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `syncing now and the last outcome are the sync's`() = runBlocking {
        coEvery { sync.syncNow() } returns SyncOutcome.Offline
        outcome.value = SyncOutcome.NoAccount

        assertEquals(SyncOutcome.Offline, repository.syncNow())
        assertEquals(SyncOutcome.NoAccount, repository.lastSync.value)
    }

    @Test
    fun `the sync status joins the outcome, whether one runs and the last good time`() =
        runBlocking {
            val at = Instant.parse("2026-10-06T08:00:00Z")
            repository.syncStatus.test {
                assertEquals(SyncStatus(SyncStatus.Phase.NEVER, null), awaitItem())

                lastSync.recordOk(at)
                outcome.value = SyncOutcome.Ok(ProcessResult())
                assertEquals(SyncStatus(SyncStatus.Phase.OK, at), awaitItem())

                syncing.value = true
                assertEquals(SyncStatus(SyncStatus.Phase.SYNCING, at), awaitItem())

                syncing.value = false
                outcome.value = SyncOutcome.Unauthorized
                var latest = awaitItem()
                while (latest.phase != SyncStatus.Phase.REFUSED) latest = awaitItem()
                assertEquals(SyncStatus(SyncStatus.Phase.REFUSED, at), latest)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `lists the calendars in the server's order with their switch`() = runBlocking {
        val id = account()
        val work = calendar(id, "Work", order = 2)
        val home = calendar(id, "Home", order = 1, color = null)
        val holidays = calendar(id, "Holidays", writable = false)
        db.calendarSettingsDao().save(CalendarSettingsEntity(work.value, null, null, false))
        // A rename alone leaves the calendar on.
        db.calendarSettingsDao().save(CalendarSettingsEntity(home.value, "Mine", null, null))
        // Another account's calendars are not listed.
        calendar(account(SignedInAccount("https://other.example.com/", "bo")), "Other")

        repository.calendars.test {
            assertEquals(emptyList<CalDavCalendarItem>(), awaitItem())

            active.value = signedIn
            var items = awaitItem()
            while (items.isEmpty()) items = awaitItem()

            assertEquals(listOf("Home", "Work", "Holidays"), items.map { it.name })
            assertEquals(listOf(true, false, true), items.map { it.enabled })
            assertEquals(listOf(true, true, false), items.map { it.writable })
            assertEquals(0xFF336699.toInt(), items[1].color)
            assertEquals(CalDavMapping.color(null), items[0].color)
            assertEquals(listOf(home, work, holidays), items.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no calendars while signed out or before the first sync`() = runBlocking {
        repository.calendars.test {
            assertEquals(emptyList<CalDavCalendarItem>(), awaitItem())
            active.value = signedIn
            // Signed in but no account row yet.
            assertEquals(emptyList<CalDavCalendarItem>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turning a calendar off is a visibility override, and on again forgets it`() = runBlocking {
        val id = calendar(account(), "Work")

        repository.setCalendarEnabled(id, false)
        assertEquals(false, db.calendarSettingsDao().find(id.value)?.visible)
        assertTrue(scheduler.calls.isEmpty())

        repository.setCalendarEnabled(id, true)
        assertNull(db.calendarSettingsDao().find(id.value))
        // Turning it on pulls it again soon.
        assertEquals(listOf("soon"), scheduler.calls)
    }

    @Test
    fun `turning a calendar off or on keeps its name and color overrides`() = runBlocking {
        val id = calendar(account(), "Work")
        db.calendarSettingsDao().save(CalendarSettingsEntity(id.value, "Job", 0xFF0000, null))

        repository.setCalendarEnabled(id, false)
        assertEquals(
            CalendarSettingsEntity(id.value, "Job", 0xFF0000, false),
            db.calendarSettingsDao().find(id.value)
        )

        repository.setCalendarEnabled(id, true)
        assertEquals(
            CalendarSettingsEntity(id.value, "Job", 0xFF0000, null),
            db.calendarSettingsDao().find(id.value)
        )
    }

    @Test
    fun `signing out revokes the login, stops the syncs and deletes everything of the account`() =
        runBlocking {
            active.value = signedIn
            val id = account()
            val calendar = calendar(id, "A")
            val other = db.davAccountDao().insert(
                DavAccountEntity(serverUrl = "https://other.example/", loginName = "bob")
            )
            lastSync.recordOk(Instant.parse("2026-10-06T08:00:00Z"))

            repository.signOut()

            coVerify(exactly = 1) { logout() }
            assertEquals(listOf("cancel"), scheduler.calls)
            assertNull(db.davAccountDao().get(id))
            assertEquals(emptyList<DavCalendarEntity>(), db.davCalendarDao().all(id))
            assertNull(db.davCalendarDao().get(CalDavIds.rowOf(calendar)))
            assertEquals(other, db.davAccountDao().get(other)?.id)
            assertNull(lastSync.lastOk())
            assertEquals(1, removedCalls)
        }

    @Test
    fun `signing out also forgets queued changes, overrides, the default and invitation records`() =
        runBlocking {
            active.value = signedIn
            val id = account()
            val mine = calendar(id, "Mine")
            val otherAccount = db.davAccountDao().insert(
                DavAccountEntity(serverUrl = "https://other.example/", loginName = "bob")
            )
            // Pending operations of both accounts: only this account's go.
            val queue = db.pendingOperationDao()
            queue.insert(
                PendingOperationEntity(
                    accountId = id,
                    type = OperationType.CREATE,
                    eventId = 1,
                    createdAt = 0
                )
            )
            queue.insert(
                PendingOperationEntity(
                    accountId = otherAccount,
                    type = OperationType.CREATE,
                    eventId = 2,
                    createdAt = 0
                )
            )
            // What lives apart from the account, keyed by calendar: a provider calendar (id 7) is
            // left alone.
            db.calendarSettingsDao().save(CalendarSettingsEntity(mine.value, "Mine", null, false))
            db.calendarSettingsDao().save(CalendarSettingsEntity(7, "Google", null, null))
            db.notifiedInvitationDao().replaceAll(
                listOf(notified(mine.value, 1), notified(7, 2))
            )
            db.reRemindDao().apply(
                listOf(
                    ReRemindEntity(mine.value, 1, "HOUR_BEFORE", 100, 90, false),
                    ReRemindEntity(7, 2, "HOUR_BEFORE", 100, 90, false)
                ),
                emptyList()
            )

            repository.signOut()

            assertEquals(emptyList<PendingOperationEntity>(), queue.all(id))
            assertEquals(1, queue.all(otherAccount).size)
            assertEquals(listOf(7L), db.calendarSettingsDao().all().map { it.calendarId })
            assertEquals(listOf(7L), db.notifiedInvitationDao().all().map { it.calendarId })
            assertEquals(listOf(7L), db.reRemindDao().all().map { it.calendarId })
        }

    @Test
    fun `signing out forgets the overrides waiting for this account but not another's`() =
        runBlocking {
            active.value = signedIn
            val id = account()
            calendar(id, "Mine")
            val mine = "ana@cloud.example.com"
            db.pendingCalendarOverrideDao().save(
                listOf(
                    PendingCalendarOverrideEntity(mine, "Work", "Job", null, null),
                    PendingCalendarOverrideEntity("bob@other.example", "Work", "Bob", null, null)
                )
            )

            repository.signOut()

            assertEquals(
                listOf("bob@other.example"),
                db.pendingCalendarOverrideDao().all().map { it.accountName }
            )
        }

    @Test
    fun `signing out before the first sync still forgets the overrides waiting for it`() =
        runBlocking {
            // No row for the account yet: the sync that creates it never ran.
            active.value = signedIn
            db.pendingCalendarOverrideDao().save(
                listOf(
                    PendingCalendarOverrideEntity("ana@cloud.example.com", "Work", "Job", 1, null)
                )
            )

            repository.signOut()

            assertEquals(
                emptyList<PendingCalendarOverrideEntity>(),
                db.pendingCalendarOverrideDao().all()
            )
        }

    @Test
    fun `signing out without a stored account still forgets the login`() = runBlocking {
        repository.signOut()

        coVerify(exactly = 1) { logout() }
        assertEquals(1, removedCalls)
    }

    @Test
    fun `signing out an account without calendars deletes the account row`() = runBlocking {
        active.value = signedIn
        val id = account()

        repository.signOut()

        assertNull(db.davAccountDao().get(id))
    }

    private fun notified(calendar: Long, event: Long) = NotifiedInvitationEntity(
        calendarId = calendar,
        eventId = event,
        title = "Invitation",
        allDay = false,
        start = 1,
        end = 2,
        zone = "UTC",
        location = null,
        organizer = null
    )
}
