// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.local.entity.PendingOperationEntity
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.local.model.OperationType
import com.qtekfun.ultimatecalendar.domain.auth.Logout
import com.qtekfun.ultimatecalendar.sync.CalDavSync
import com.qtekfun.ultimatecalendar.sync.RecordingCalDavScheduler
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CalDavAccountRepositoryTest {
    private val signedIn = SignedInAccount("https://cloud.example.com/", "ana")
    private val active = MutableStateFlow<SignedInAccount?>(null)
    private val session = mockk<AccountSession> { every { activeAccount } returns active }
    private val db = inMemoryDatabase()
    private val scheduler = RecordingCalDavScheduler()
    private val sync = mockk<CalDavSync>()
    private val logout = mockk<Logout>(relaxed = true)
    private val repository =
        CalDavAccountRepository(session, db, sync, scheduler, logout, Dispatchers.IO)

    @AfterEach
    fun close() = db.close()

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
        every { sync.lastOutcome } returns MutableStateFlow(SyncOutcome.NoAccount)

        assertEquals(SyncOutcome.Offline, repository.syncNow())
        assertEquals(SyncOutcome.NoAccount, repository.lastSync.value)
    }

    @Test
    fun `signing out revokes the login, stops the syncs and deletes everything of the account`() =
        runBlocking {
            active.value = signedIn
            val id = db.davAccountDao().insert(
                DavAccountEntity(serverUrl = signedIn.serverUrl, loginName = "ana")
            )
            db.davCalendarDao().insert(DavCalendarEntity(accountId = id, href = "/a/", name = "A"))
            val other = db.davAccountDao().insert(
                DavAccountEntity(serverUrl = "https://other.example/", loginName = "bob")
            )

            repository.signOut()

            coVerify(exactly = 1) { logout() }
            assertEquals(listOf("cancel"), scheduler.calls)
            assertNull(db.davAccountDao().get(id))
            assertEquals(emptyList<DavCalendarEntity>(), db.davCalendarDao().all(id))
            assertEquals(other, db.davAccountDao().get(other)?.id)
        }

    @Test
    fun `signing out without a stored account still forgets the login`() = runBlocking {
        repository.signOut()

        coVerify(exactly = 1) { logout() }
    }
}
