// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What the user typed as the address of a subscription, checked and normalized. */
sealed interface ParsedSubscriptionUrl {
    /** [url] is the https address to download; [host] is the only part safe to show or log. */
    data class Valid(val url: String, val host: String) : ParsedSubscriptionUrl

    /** Plain http: refused, HTTPS is mandatory (SPEC §6). */
    data object Insecure : ParsedSubscriptionUrl

    data object Invalid : ParsedSubscriptionUrl
}

/**
 * Turns what the user typed into the address to download. `webcal://` and `webcals://` (what a
 * "subscribe" link gives) become `https://`; an address without a scheme is taken as https;
 * plain `http://` is refused. Anything else, an address with a user name or password in it and
 * one that is absurdly long are invalid. The path and query are kept as they are, since many
 * feeds keep a secret token there; the fragment is dropped.
 */
object SubscriptionUrls {
    private const val MAX_LENGTH = 2_048
    private val WEBCAL = Regex("^webcals?://", RegexOption.IGNORE_CASE)
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    fun parse(input: String): ParsedSubscriptionUrl = parse(input, allowInsecure = false)

    /** [allowInsecure] exists only so tests can talk to a local plain-http server. */
    internal fun parse(input: String, allowInsecure: Boolean): ParsedSubscriptionUrl {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return ParsedSubscriptionUrl.Invalid
        val text = when {
            WEBCAL.containsMatchIn(trimmed) -> trimmed.replace(WEBCAL, "https://")
            SCHEME.containsMatchIn(trimmed) -> trimmed
            else -> "https://$trimmed"
        }
        val url = text.toHttpUrlOrNull()
        return when {
            url == null || url.host.isEmpty() -> ParsedSubscriptionUrl.Invalid

            url.username.isNotEmpty() || url.password.isNotEmpty() -> ParsedSubscriptionUrl.Invalid

            !url.isHttps && !allowInsecure -> ParsedSubscriptionUrl.Insecure

            else -> ParsedSubscriptionUrl.Valid(
                url.newBuilder().fragment(null).build().toString(),
                url.host
            )
        }
    }
}
