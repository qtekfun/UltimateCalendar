// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

/**
 * The tags that tell the notifications of the invitations apart. A notification is identified by
 * its tag and a fixed id, so showing the same invitation twice replaces it, and no id can collide
 * with another kind. Tags carry database ids only, never titles or addresses.
 */
object NotificationTags {
    const val SUMMARY = "invitations/summary"
    private const val INVITATION = "invitation/"
    private const val MOVED = "moved/"
    private const val CANCELLED = "cancelled/"

    fun invitation(key: InvitationKey) = INVITATION + id(key)

    fun moved(key: InvitationKey) = MOVED + id(key)

    fun cancelled(key: InvitationKey) = CANCELLED + id(key)

    /** Whether [tag] belongs to a notification that asks for an answer (a member of the group). */
    fun isInvitation(tag: String?) = tag != null && tag.startsWith(INVITATION)

    // The address of an account is not put in the tag, only a digest of it.

    /** The ids that name [key] in a tag: calendar, event and, for another account, a digest. */
    fun id(key: InvitationKey) = "${key.calendarId.value}/${key.eventId.value}" +
        if (key.isForeign) "/" + key.address.hashCode().toUInt().toString(HEX) else ""

    private const val HEX = 16
}
