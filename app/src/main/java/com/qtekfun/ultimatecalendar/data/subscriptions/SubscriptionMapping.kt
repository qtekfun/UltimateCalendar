// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.data.local.entity.SubscriptionEntity
import com.qtekfun.ultimatecalendar.domain.subscriptions.RefreshInterval
import com.qtekfun.ultimatecalendar.domain.subscriptions.Subscription
import com.qtekfun.ultimatecalendar.domain.subscriptions.SubscriptionError
import java.time.Instant

/** A stored subscription as the domain sees it; the address stays behind. */
internal fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    name = name,
    color = color,
    enabled = enabled,
    interval = RefreshInterval.ofHours(refreshHours) ?: RefreshInterval.DEFAULT,
    host = host,
    lastAttemptAt = lastAttemptAt?.let(Instant::ofEpochMilli),
    lastSuccessAt = lastSuccessAt?.let(Instant::ofEpochMilli),
    error = error?.let { name ->
        SubscriptionError.entries.firstOrNull { it.name == name } ?: SubscriptionError.NETWORK
    },
    errorCode = errorCode,
    eventCount = eventCount,
    skipped = skipped
)
