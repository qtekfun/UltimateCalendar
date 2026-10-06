// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

/** Subscription feeds as text, for the tests. */
object FeedIcs {
    fun calendar(vararg parts: String) =
        "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//Test//EN\n" + parts.joinToString("") +
            "END:VCALENDAR\n"

    /** A `VEVENT`; [extra] are more content lines. */
    fun event(
        uid: String?,
        summary: String,
        start: String = "20261006T100000Z",
        end: String = "20261006T110000Z",
        extra: String = ""
    ) = "BEGIN:VEVENT\n" +
        (uid?.let { "UID:$it\n" } ?: "") +
        "DTSTAMP:20261001T000000Z\nDTSTART:$start\nDTEND:$end\nSUMMARY:$summary\n" +
        extra + "END:VEVENT\n"

    /** An event with guests and an alarm, which a subscription must not keep. */
    fun invitation(uid: String = "inv@x", me: String = "me@example.com") = event(
        uid,
        "Planning",
        extra = "ORGANIZER:mailto:boss@example.com\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;CN=Me:mailto:$me\n" +
            "BEGIN:VALARM\nACTION:DISPLAY\nTRIGGER:-PT10M\nDESCRIPTION:x\nEND:VALARM\n"
    )

    /** Something that is not an event, which feeds contain and which is ignored. */
    const val TODO =
        "BEGIN:VTODO\nUID:todo@x\nDTSTAMP:20261001T000000Z\nSUMMARY:Buy milk\nEND:VTODO\n"

    const val JOURNAL =
        "BEGIN:VJOURNAL\nUID:j@x\nDTSTAMP:20261001T000000Z\nSUMMARY:Diary\nEND:VJOURNAL\n"
}
