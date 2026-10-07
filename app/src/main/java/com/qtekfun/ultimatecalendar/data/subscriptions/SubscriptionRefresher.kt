// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.data.local.StoredSubscriptionEvents
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.subscriptions.Subscription
import com.qtekfun.ultimatecalendar.domain.subscriptions.SubscriptionError
import com.qtekfun.ultimatecalendar.domain.subscriptions.SubscriptionPolicy
import com.qtekfun.ultimatecalendar.notify.SystemZone
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How refreshing one subscription ended. */
sealed interface RefreshResult {
    /** A new copy was read: [events] series stored, [skipped] events could not be read. */
    data class Updated(val events: Int, val skipped: Int) : RefreshResult

    /** The server says nothing changed; the stored events stay. */
    data object Unchanged : RefreshResult

    data class Failed(val error: SubscriptionError, val httpCode: Int? = null) : RefreshResult

    /** The subscription was removed meanwhile. */
    data object Gone : RefreshResult
}

/** What a run over several subscriptions found. */
data class RefreshSummary(val attempted: Int, val temporaryFailures: Int)

/**
 * Downloads subscriptions and keeps the result in Room. A refresh that fails keeps the events
 * already stored and records the error and the time; one that succeeds, even if nothing changed,
 * clears the error and records the time. Events keep their ids across downloads (the UID is
 * their identity), so what is open on the screen survives a refresh. Refreshes never overlap.
 */
@Singleton
class SubscriptionRefresher @Inject constructor(
    private val database: UltimateCalendarDatabase,
    private val downloader: SubscriptionDownloader,
    private val vault: SubscriptionUrlVault,
    private val zone: SystemZone,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : SubscriptionsRefresh {
    private val subscriptions = database.subscriptionDao()
    private val events = database.subscriptionEventDao()
    private val mutex = Mutex()
    private val running = MutableStateFlow(emptySet<Long>())

    /** The subscriptions being downloaded right now. */
    val refreshing: StateFlow<Set<Long>> = running.asStateFlow()

    suspend fun refresh(id: Long): RefreshResult = withContext(io) {
        running.update { it + id }
        try {
            mutex.withLock { refreshLocked(id) }
        } finally {
            running.update { it - id }
        }
    }

    /** Refreshes every enabled subscription (the user asked, or one was just added). */
    override suspend fun refreshAll(): RefreshSummary = refreshEach { _, all ->
        all.filter { it.enabled }
    }

    /**
     * Refreshes what is due at this moment (see [SubscriptionPolicy.toRefresh]); [retrying] is
     * true on a retry after a failure.
     */
    suspend fun refreshDue(retrying: Boolean): RefreshSummary = refreshEach { now, all ->
        SubscriptionPolicy.toRefresh(all, now, retrying)
    }

    private suspend fun refreshEach(
        choose: (Instant, List<Subscription>) -> List<Subscription>
    ): RefreshSummary {
        val all = withContext(io) { subscriptions.all() }.map { it.toDomain() }
        val chosen = choose(clock.instant(), all)
        val failures = chosen.map { refresh(it.id) }
            .count {
                it is RefreshResult.Failed &&
                    SubscriptionPolicy.isTemporary(it.error, it.httpCode)
            }
        return RefreshSummary(chosen.size, failures)
    }

    private suspend fun refreshLocked(id: Long): RefreshResult {
        val row = subscriptions.get(id) ?: return RefreshResult.Gone
        val url = vault.open(row.urlSecret)
        return if (url == null) {
            failed(id, SubscriptionError.UNREADABLE_URL, null)
        } else {
            when (val fetched = downloader.fetch(url, row.etag, row.lastModified)) {
                is FetchResult.Failed -> failed(id, fetched.error, fetched.httpCode)
                FetchResult.NotModified -> succeeded(id, null)
                is FetchResult.Fresh -> store(id, fetched)
            }
        }
    }

    private suspend fun store(id: Long, fresh: FetchResult.Fresh): RefreshResult {
        val feed = try {
            SubscriptionFeed.parse(fresh.body, zone.current())
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: DateTimeException) {
            null
        } ?: return failed(id, SubscriptionError.NOT_CALENDAR, null)
        return succeeded(id, Stored(feed, fresh))
    }

    private class Stored(val feed: SubscriptionEvents, val fresh: FetchResult.Fresh)

    private suspend fun succeeded(id: Long, stored: Stored?): RefreshResult =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val row = subscriptions.get(id) ?: return@immediateTransaction RefreshResult.Gone
                val now = clock.millis()
                val base = row.copy(
                    lastAttemptAt = now,
                    lastSuccessAt = now,
                    error = null,
                    errorCode = null
                )
                if (stored == null) {
                    subscriptions.update(base)
                    RefreshResult.Unchanged
                } else {
                    replaceEvents(id, stored.feed.events)
                    subscriptions.update(
                        base.copy(
                            etag = stored.fresh.etag,
                            lastModified = stored.fresh.lastModified,
                            eventCount = stored.feed.events.size,
                            skipped = stored.feed.skipped
                        )
                    )
                    RefreshResult.Updated(stored.feed.events.size, stored.feed.skipped)
                }
            }
        }

    /** Makes the stored events the ones of [feed]: changed ones updated in place, gone ones deleted. */
    private suspend fun replaceEvents(id: Long, feed: List<IcsEvent>) {
        val existing = events.inSubscription(id).associateBy { it.uid }
        feed.forEach { event ->
            val old = existing[event.uid]
            val row = StoredSubscriptionEvents.write(id, old?.id ?: 0, event)
            when {
                old == null -> events.insert(row)
                row != old -> events.update(row)
            }
        }
        val kept = feed.mapTo(HashSet()) { it.uid }
        existing.values.filter { it.uid !in kept }.forEach { events.delete(it.id) }
    }

    private suspend fun failed(id: Long, error: SubscriptionError, code: Int?): RefreshResult {
        val row = subscriptions.get(id) ?: return RefreshResult.Gone
        subscriptions.update(
            row.copy(lastAttemptAt = clock.millis(), error = error.name, errorCode = code)
        )
        return RefreshResult.Failed(error, code)
    }
}
