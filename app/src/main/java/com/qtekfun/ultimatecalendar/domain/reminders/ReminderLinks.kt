// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import java.net.URLEncoder

/**
 * The links a reminder can offer (RF-07): the video call of an event, found in its location or
 * description, and a map for a location that is a place. Pure text work: no event is read here.
 */
object ReminderLinks {
    private val URL = Regex("""https?://[^\s<>"')]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?"

    private val CALL_HOSTS = listOf(
        "meet.google.com",
        "zoom.us",
        "zoomgov.com",
        "teams.microsoft.com",
        "teams.live.com",
        "whereby.com"
    )

    /** The first video-call link in [texts], in order, or null. */
    fun videoCall(vararg texts: String?): String? = texts.asSequence()
        .filterNotNull()
        .flatMap { text -> URL.findAll(text).map { it.value.trimEnd { c -> c in TRAILING } } }
        .firstOrNull(::isCall)

    private fun isCall(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore('/').substringBefore('?')
            .substringAfterLast('@').substringBefore(':').lowercase()
        return "jitsi" in host || host == "meet.jit.si" ||
            CALL_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /** A `geo:` link to search [location] on a map, or null when it is empty or a web link. */
    fun map(location: String?): String? {
        val place = location?.trim().orEmpty()
        if (place.isEmpty() || URL.containsMatchIn(place)) return null
        return "geo:0,0?q=" + URLEncoder.encode(place, "UTF-8").replace("+", "%20")
    }
}
