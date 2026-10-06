// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.data.auth.FakeCipher
import com.qtekfun.ultimatecalendar.data.local.entity.SubscriptionEntity
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.subscription.SubscriptionCalendarSource
import com.qtekfun.ultimatecalendar.notify.SystemZone
import com.qtekfun.ultimatecalendar.sync.RecordingSubscriptionScheduler
import com.qtekfun.ultimatecalendar.sync.queue.MutableClock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient

/**
 * Subscriptions over an in-memory Room, the real downloader talking to MockWebServer (plain
 * http, which only tests may use), the real refresher, repository and source, and a fake cipher.
 */
class SubscriptionRig(
    private val server: MockWebServer,
    zone: ZoneId = ZoneId.of("Europe/Madrid"),
    private val zoneOf: SystemZone = SystemZone { zone }
) {
    val db = inMemoryDatabase()
    val clock = MutableClock(Instant.parse("2026-10-06T08:00:00Z"))
    val vault = SubscriptionUrlVault(FakeCipher())
    val scheduler = RecordingSubscriptionScheduler()
    val downloader = SubscriptionDownloader(OkHttpClient(), Dispatchers.Unconfined, true)
    val refresher =
        SubscriptionRefresher(db, downloader, vault, zoneOf, clock, Dispatchers.Unconfined)
    val repository = SubscriptionRepository(db, vault, scheduler, Dispatchers.Unconfined)
    val source = SubscriptionCalendarSource(db, Dispatchers.Unconfined)
    val subscriptions = db.subscriptionDao()
    val events = db.subscriptionEventDao()

    /** A subscription to [path] of the server, added as the repository would (but plain http). */
    suspend fun addRow(
        path: String = "/feed.ics",
        name: String = "Holidays",
        hours: Int = 12,
        enabled: Boolean = true
    ): Long {
        val url = server.url(path).toString()
        return subscriptions.insert(
            SubscriptionEntity(
                name = name,
                color = 0xFF039BE5.toInt(),
                enabled = enabled,
                refreshHours = hours,
                host = server.hostName,
                urlKey = vault.keyOf(url),
                urlSecret = vault.seal(url)
            )
        )
    }

    fun close() = db.close()
}
