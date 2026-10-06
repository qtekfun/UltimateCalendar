// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionUrlsTest {
    private fun valid(url: String, host: String) = ParsedSubscriptionUrl.Valid(url, host)

    @Test
    fun `webcal becomes https`() {
        assertEquals(
            valid("https://cal.example.com/feed.ics", "cal.example.com"),
            SubscriptionUrls.parse("webcal://cal.example.com/feed.ics")
        )
        assertEquals(
            valid("https://cal.example.com/feed.ics", "cal.example.com"),
            SubscriptionUrls.parse("WEBCALS://cal.example.com/feed.ics")
        )
    }

    @Test
    fun `https is kept as it is, with the secret token in the path and query`() {
        assertEquals(
            valid(
                "https://cal.example.com/private/abc123/basic.ics?token=s3cr3t",
                "cal.example.com"
            ),
            SubscriptionUrls.parse(
                " https://cal.example.com/private/abc123/basic.ics?token=s3cr3t "
            )
        )
    }

    @Test
    fun `an address without a scheme is taken as https and the fragment is dropped`() {
        assertEquals(
            valid("https://cal.example.com/feed.ics", "cal.example.com"),
            SubscriptionUrls.parse("cal.example.com/feed.ics#top")
        )
    }

    @Test
    fun `plain http is refused, however it is written`() {
        assertEquals(
            ParsedSubscriptionUrl.Insecure,
            SubscriptionUrls.parse("http://example.com/a.ics")
        )
        assertEquals(
            ParsedSubscriptionUrl.Insecure,
            SubscriptionUrls.parse("HTTP://example.com/a.ics")
        )
    }

    @Test
    fun `plain http is accepted only where tests allow it`() {
        assertEquals(
            valid("http://localhost:8080/a.ics", "localhost"),
            SubscriptionUrls.parse("http://localhost:8080/a.ics", allowInsecure = true)
        )
    }

    @Test
    fun `other schemes, empty text, credentials and absurd lengths are invalid`() {
        listOf(
            "",
            "   ",
            "ftp://example.com/a.ics",
            "file:///sdcard/a.ics",
            "https://",
            "https://user:pass@example.com/a.ics",
            "https://user@example.com/a.ics",
            "not a url at all",
            "https://example.com/" + "a".repeat(3000)
        ).forEach {
            assertEquals(ParsedSubscriptionUrl.Invalid, SubscriptionUrls.parse(it), it.take(40))
        }
    }
}
