// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.data.ical.IcsEvents
import com.qtekfun.ultimatecalendar.data.ical.IcsWriter
import com.qtekfun.ultimatecalendar.data.local.StoredSeries
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDav
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDavProvider
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavResult
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.sync.conflict.EventField
import com.qtekfun.ultimatecalendar.sync.conflict.event as sampleEvent
import com.qtekfun.ultimatecalendar.sync.queue.FixedRandom
import com.qtekfun.ultimatecalendar.sync.queue.MutableClock
import com.qtekfun.ultimatecalendar.sync.queue.OperationQueue
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient

/**
 * A database, the real queue, merger and resolver, and a CalDAV client talking to
 * [FakeCalDav] through [server], for the sync tests.
 */
class EngineFixtures(val server: MockWebServer, zone: ZoneId = ZoneId.of("Europe/Madrid")) {
    val fake = FakeCalDav()
    val db = inMemoryDatabase()
    val clock = MutableClock(zone = zone)
    val queue = OperationQueue(db, clock, FixedRandom(0.5))
    val merger = EventMerger(db, queue, clock.zone)
    val pull = PullSync(db, queue, clock)
    val push = PushSync(db, queue, clock)
    val events = db.davEventDao()
    val calendars = db.davCalendarDao()
    lateinit var account: DavAccountEntity
    lateinit var dav: CalDav
    lateinit var work: String

    suspend fun setUp(): EngineFixtures {
        server.dispatcher = fake
        val signedIn = SignedInAccount(server.url("/").toString(), "ana")
        val id = db.davAccountDao().insert(
            DavAccountEntity(serverUrl = signedIn.serverUrl, loginName = signedIn.loginName)
        )
        account = requireNotNull(db.davAccountDao().get(id))
        dav = requireNotNull(
            CalDavProvider(OkHttpClient(), { Credentials("ana", "secret") }, Dispatchers.Unconfined)
                .connect(signedIn, allowInsecure = true)
        )
        work = fake.addCalendar("Work")
        return this
    }

    /** The text of an event resource as another client would have written it. */
    fun ics(event: IcsEvent = sampleEvent()): String =
        IcsWriter.write(IcsEvents.write(null, event, clock.instant(), clock.zone))

    suspend fun pullAll(): DavResult<*>? =
        pull.pull(dav, requireNotNull(db.davAccountDao().get(account.id)))

    suspend fun pushAll() = push.push(dav, account.id)

    suspend fun calendar(href: String = work): DavCalendarEntity =
        requireNotNull(calendars.byHref(account.id, href))

    suspend fun row(href: String): DavEventEntity = requireNotNull(events.byHref(account.id, href))

    /** The event as the series the rows hold, for comparing with what was written. */
    suspend fun series(href: String): IcsEvent = StoredSeries.read(row(href))

    /**
     * A new event created here, as the editor will: the row with its dirty fields, the create
     * queued. Returns the row.
     */
    suspend fun createLocally(
        href: String,
        event: IcsEvent = sampleEvent(),
        dirty: Int = 0,
        calendarHref: String = work
    ): DavEventEntity {
        val row = StoredSeries.create(account.id, calendar(calendarHref).id, href, event)
            .copy(dirtyFields = dirty, modifiedAt = clock.millis())
        val id = events.insert(row)
        queue.enqueue(account.id, id, QueuedOperation.CreateEvent)
        return row.copy(id = id)
    }

    /** An event the server has and Room already pulled. Returns the row. */
    suspend fun pulled(href: String, event: IcsEvent = sampleEvent()): DavEventEntity {
        fake.put(href, ics(event))
        pullAll()
        return row(href)
    }

    /**
     * An edit made here, as the editor will: the row changed, the fields marked dirty, the time of
     * the change kept and an update queued. Returns the row.
     */
    suspend fun editLocally(
        href: String,
        vararg fields: EventField,
        at: Instant = clock.instant(),
        change: (IcsEvent) -> IcsEvent
    ): DavEventEntity {
        val row = row(href)
        val edited = StoredSeries.write(row, change(StoredSeries.read(row))).copy(
            dirtyFields = row.dirtyFields or EventField.toBits(fields.toSet()),
            modifiedAt = at.toEpochMilli()
        )
        events.update(edited)
        queue.enqueue(account.id, row.id, QueuedOperation.UpdateEvent)
        return edited
    }

    suspend fun operations() = db.pendingOperationDao().all(account.id).map {
        QueuedOperation.decode(it.payload)
    }
}

/** The event with its master changed. */
fun IcsEvent.master(change: Event.() -> Event) =
    copy(series = series.copy(event = series.event.change()))
