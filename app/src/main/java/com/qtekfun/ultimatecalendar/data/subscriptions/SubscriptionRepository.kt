// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.SubscriptionEntity
import com.qtekfun.ultimatecalendar.data.settings.backup.BackupSubscription
import com.qtekfun.ultimatecalendar.data.source.SubscriptionIds
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.editor.EventColorChoice
import com.qtekfun.ultimatecalendar.domain.subscriptions.RefreshInterval
import com.qtekfun.ultimatecalendar.domain.subscriptions.Subscription
import com.qtekfun.ultimatecalendar.domain.subscriptions.SubscriptionPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The outcome of adding a subscription. */
sealed interface AddResult {
    data class Added(val id: Long) : AddResult

    /** Plain http: refused. */
    data object Insecure : AddResult

    data object Invalid : AddResult

    /** The same address is subscribed already. */
    data object Duplicate : AddResult
}

/** What a restore of subscriptions did: [added] are new, [refused] had an address that is not https. */
data class RestoredSubscriptions(val added: Int, val refused: Int)

/**
 * The user's subscriptions (T39): adding, editing and removing them, and keeping the periodic
 * refresh in step (none without a subscription that refreshes by itself). The address is stored
 * encrypted and leaves this class only for the downloader and for the backup, whose content is
 * sealed.
 */
@Singleton
class SubscriptionRepository @Inject constructor(
    private val database: UltimateCalendarDatabase,
    private val vault: SubscriptionUrlVault,
    private val scheduler: SubscriptionScheduler,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val dao = database.subscriptionDao()

    val subscriptions: Flow<List<Subscription>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }.flowOn(io)

    suspend fun all(): List<Subscription> = withContext(io) { dao.all().map { it.toDomain() } }

    /**
     * Subscribes to [input] (what the user typed or pasted). A blank [name] is replaced by the
     * address' host. Downloading it is the caller's next step ([SubscriptionRefresher.refresh]).
     */
    suspend fun add(input: String, name: String, color: Int, interval: RefreshInterval): AddResult =
        withContext(io) {
            when (val parsed = SubscriptionUrls.parse(input)) {
                ParsedSubscriptionUrl.Insecure -> AddResult.Insecure

                ParsedSubscriptionUrl.Invalid -> AddResult.Invalid

                is ParsedSubscriptionUrl.Valid ->
                    insert(parsed, name, color, interval, true).also {
                        if (it is AddResult.Added) reschedule()
                    }
            }
        }

    private suspend fun insert(
        url: ParsedSubscriptionUrl.Valid,
        name: String,
        color: Int,
        interval: RefreshInterval,
        enabled: Boolean
    ): AddResult {
        val key = vault.keyOf(url.url)
        if (dao.byUrlKey(key) != null) return AddResult.Duplicate
        val id = dao.insert(
            SubscriptionEntity(
                name = SubscriptionPolicy.cleanName(name, url.host),
                color = color,
                enabled = enabled,
                refreshHours = interval.hours,
                host = url.host,
                urlKey = key,
                urlSecret = vault.seal(url.url)
            )
        )
        return AddResult.Added(id)
    }

    suspend fun rename(id: Long, name: String, color: Int) = change(id) {
        it.copy(name = SubscriptionPolicy.cleanName(name, it.host), color = color)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = change(id) { it.copy(enabled = enabled) }

    suspend fun setInterval(id: Long, interval: RefreshInterval) =
        change(id) { it.copy(refreshHours = interval.hours) }

    /** Removes the subscription and, with it, its events and the local settings of its calendar. */
    suspend fun remove(id: Long) = withContext(io) {
        dao.delete(id)
        database.calendarSettingsDao().clear(SubscriptionIds.calendar(id).value)
        reschedule()
    }

    /** The subscriptions for a backup, with their addresses; only for sealed content. */
    suspend fun forBackup(): List<BackupSubscription> = withContext(io) {
        dao.all().mapNotNull { row ->
            vault.open(row.urlSecret)?.let { url ->
                BackupSubscription(row.name, url, row.color, row.enabled, row.refreshHours)
            }
        }
    }

    /**
     * Subscribes to what a backup holds. Every entry is checked as if typed by the user: one whose
     * address is not https is refused, and unknown or missing values take the defaults. Entries
     * already subscribed are left as they are.
     */
    suspend fun restore(entries: List<BackupSubscription>): RestoredSubscriptions =
        withContext(io) {
            var added = 0
            var refused = 0
            entries.forEach { entry ->
                val parsed = SubscriptionUrls.parse(entry.url)
                if (parsed is ParsedSubscriptionUrl.Valid) {
                    val result = insert(
                        parsed,
                        entry.name,
                        entry.color ?: EventColorChoice.PEACOCK.argb,
                        entry.refreshHours?.let(
                            RefreshInterval::ofHours
                        ) ?: RefreshInterval.DEFAULT,
                        entry.enabled ?: true
                    )
                    if (result is AddResult.Added) added++
                } else {
                    refused++
                }
            }
            if (added > 0) scheduler.refreshNow()
            reschedule()
            RestoredSubscriptions(added, refused)
        }

    private suspend fun change(id: Long, edit: (SubscriptionEntity) -> SubscriptionEntity) =
        withContext(io) {
            dao.get(id)?.let { dao.update(edit(it)) }
            reschedule()
        }

    /** Called after every change that can alter what the periodic work should be. */
    suspend fun reschedule() = withContext(io) {
        scheduler.apply(SubscriptionPolicy.plan(dao.all().map { it.toDomain() }))
    }
}
