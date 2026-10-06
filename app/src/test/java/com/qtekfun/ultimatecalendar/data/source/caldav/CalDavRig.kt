// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.source.SourceUnderTest
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.engine.EngineFixtures
import com.qtekfun.ultimatecalendar.sync.engine.FakeCalDav
import com.qtekfun.ultimatecalendar.sync.engine.PullSync
import com.qtekfun.ultimatecalendar.sync.engine.PushSync
import com.qtekfun.ultimatecalendar.sync.engine.SyncEngine
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import com.qtekfun.ultimatecalendar.sync.engine.SyncSession
import com.qtekfun.ultimatecalendar.sync.engine.SyncSource
import com.qtekfun.ultimatecalendar.sync.queue.FixedRandom
import com.qtekfun.ultimatecalendar.sync.queue.OperationQueue
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import mockwebserver3.MockWebServer

/**
 * A [CalDavCalendarSource] over an in-memory Room and a fake CalDAV server behind MockWebServer,
 * with the real queue, merger, resolver and sync engine. The server has the user's calendar
 * "Mine" and "Holidays", which is read-only, and knows the user as me@example.com. Calendars come
 * from a first sync, as in the app.
 */
class CalDavRig(server: MockWebServer, zone: ZoneId = ZoneId.of("Europe/Madrid")) {
    val env = EngineFixtures(server, zone)
    val fake get() = env.fake
    val clock get() = env.clock
    lateinit var mine: String
    lateinit var holidays: String
    lateinit var engine: SyncEngine
    lateinit var source: CalDavCalendarSource
    lateinit var accounts: CalDavAccounts

    /** How many times the source asked for a sync after a change. */
    var syncRequests = 0

    private var uid = 0
    private lateinit var signedIn: SignedInAccount

    /** [configure] adjusts the server before the first sync. */
    suspend fun setUp(configure: FakeCalDav.() -> Unit = {}): CalDavRig {
        env.setUp()
        fake.configure()
        mine = fake.addCalendar("Mine")
        holidays = fake.addCalendar("Holidays")
        fake.readOnly += holidays
        signedIn = SignedInAccount(env.account.serverUrl, env.account.loginName)
        val session = mockk<AccountSession> {
            every { activeAccount } returns MutableStateFlow(signedIn)
            every { restore() } returns signedIn
        }
        accounts = CalDavAccounts(session, env.db)
        engine = SyncEngine(
            SyncSource { SyncSession(signedIn, env.dav) },
            env.db,
            env.push,
            env.pull,
            Dispatchers.Unconfined
        )
        source = restarted()
        sync()
        return this
    }

    /** A source and an engine built again over the same database, as after the process died. */
    fun restarted(): CalDavCalendarSource = CalDavCalendarSource(
        accounts,
        env.db,
        env.queue,
        env.clock,
        { syncRequests++ },
        { "uid-${++uid}@test" },
        Dispatchers.Unconfined
    )

    suspend fun sync(): SyncOutcome = engine.sync()

    /** A queue, push, pull and engine built again over the same database, as after a restart. */
    fun restartedEngine(): SyncEngine {
        val queue = OperationQueue(env.db, env.clock, FixedRandom(0.5))
        return SyncEngine(
            SyncSource { SyncSession(signedIn, env.dav) },
            env.db,
            PushSync(env.db, queue, env.clock),
            PullSync(env.db, queue, env.clock),
            Dispatchers.Unconfined
        )
    }

    /** Moves the clock past the backoff of a failed operation. */
    fun waitOutBackoff() = env.clock.advance(Duration.ofHours(1))

    suspend fun calendars(): List<CalendarInfo> = (
        source.calendars() as CalendarResult.Success
        ).value

    suspend fun underTest(): SourceUnderTest {
        val all = calendars()
        return SourceUnderTest(
            source,
            all.first { it.displayName == "Mine" },
            all.first { it.displayName == "Holidays" }
        )
    }

    fun close() = env.db.close()
}
