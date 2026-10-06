// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.subscriptions.RefreshInterval
import com.qtekfun.ultimatecalendar.domain.subscriptions.Subscription
import com.qtekfun.ultimatecalendar.domain.subscriptions.SubscriptionError
import java.time.Instant

/** Words for subscriptions, in the language of the UI. */
@Composable
internal fun intervalName(interval: RefreshInterval): String =
    if (interval == RefreshInterval.MANUAL) {
        stringResource(R.string.subscriptions_manual)
    } else {
        pluralStringResource(R.plurals.subscriptions_every_hours, interval.hours, interval.hours)
    }

@Composable
internal fun errorText(error: SubscriptionError, httpCode: Int?): String = when (error) {
    SubscriptionError.TIMEOUT -> stringResource(R.string.subscriptions_error_timeout)
    SubscriptionError.UNREACHABLE -> stringResource(R.string.subscriptions_error_unreachable)
    SubscriptionError.TLS -> stringResource(R.string.subscriptions_error_tls)
    SubscriptionError.HTTP -> stringResource(R.string.subscriptions_error_http, httpCode ?: 0)
    SubscriptionError.TOO_LARGE -> stringResource(R.string.subscriptions_error_too_large)
    SubscriptionError.INSECURE_REDIRECT -> stringResource(R.string.subscriptions_error_insecure)
    SubscriptionError.TOO_MANY_REDIRECTS -> stringResource(R.string.subscriptions_error_redirects)
    SubscriptionError.NOT_CALENDAR -> stringResource(R.string.subscriptions_error_not_calendar)
    SubscriptionError.NETWORK -> stringResource(R.string.subscriptions_error_network)
    SubscriptionError.UNREADABLE_URL -> stringResource(R.string.subscriptions_error_unreadable)
}

/** The state line of a row: the last error, or the last update with what it read. */
@Composable
internal fun statusText(row: SubscriptionRow, now: Instant): String {
    val subscription = row.subscription
    val updated = subscription.lastSuccessAt?.let { updated(it, now) }
    return when {
        row.refreshing -> stringResource(R.string.subscriptions_refreshing)

        !subscription.enabled -> stringResource(R.string.subscriptions_disabled)

        subscription.error != null -> {
            val error = errorText(subscription.error, subscription.errorCode)
            if (updated == null) {
                error
            } else {
                stringResource(R.string.subscriptions_error_with_update, error, updated)
            }
        }

        updated == null -> stringResource(R.string.subscriptions_never)

        else -> readText(subscription, updated)
    }
}

@Composable
private fun readText(subscription: Subscription, updated: String): String {
    val events = pluralStringResource(
        R.plurals.subscriptions_events,
        subscription.eventCount,
        subscription.eventCount
    )
    val base = stringResource(R.string.subscriptions_updated, updated, events)
    return if (subscription.skipped > 0) {
        base + " " + pluralStringResource(
            R.plurals.subscriptions_skipped,
            subscription.skipped,
            subscription.skipped
        )
    } else {
        base
    }
}

private fun updated(at: Instant, now: Instant): String = DateUtils.getRelativeTimeSpanString(
    at.toEpochMilli(),
    now.toEpochMilli(),
    DateUtils.MINUTE_IN_MILLIS
).toString()
