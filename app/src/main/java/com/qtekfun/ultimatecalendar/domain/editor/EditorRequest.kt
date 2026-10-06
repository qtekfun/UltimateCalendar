// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * What opens the editor: a new event (at a tapped time, or at the next half hour) or an
 * existing one at one occurrence of its series. A request is saved with the navigation state,
 * so it is written to and read from a list of plain strings.
 */
sealed interface EditorRequest {
    /** A new event; [at] is the wall-clock time of the slot the user tapped, if any. */
    data class New(val at: LocalDateTime? = null) : EditorRequest

    /** Edits the occurrence [ref] points at (the event's own time for a single event). */
    data class Edit(val ref: EventRef) : EditorRequest

    fun encode(): List<String> = when (this) {
        is New -> listOf(NEW, at?.toEpochSecond(ZoneOffset.UTC)?.toString().orEmpty())
        is Edit -> listOf(EDIT, ref.encode())
    }

    companion object {
        private const val NEW = "new"
        private const val EDIT = "edit"

        /** The request [encode] wrote, or null if [values] is not one. */
        fun decode(values: List<String>?): EditorRequest? = runCatching {
            when (values?.firstOrNull()) {
                NEW -> New(
                    values[1].toLongOrNull()?.let {
                        LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC)
                    }
                )

                EDIT -> EventRef.decode(values[1])?.let { Edit(it) }

                else -> null
            }
        }.getOrNull()
    }
}
