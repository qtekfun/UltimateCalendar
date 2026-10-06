// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.widget

import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import java.time.LocalDate

/**
 * What a tap on a home-screen widget does (T38). It travels in an intent as text, so the app
 * can read it back; anything that is not one of these is ignored.
 */
sealed interface WidgetTap {
    /** Just opens the app: the empty and the no-permission states, the "+N more" row. */
    data object OpenApp : WidgetTap

    /** Opens the editor on a new event (the "+" of the Agenda widget). */
    data object NewEvent : WidgetTap

    /** Opens the Day view on [date] (a cell of the Month widget). */
    data class OpenDay(val date: LocalDate) : WidgetTap

    /** Opens the detail of one occurrence. */
    data class OpenEvent(val ref: EventRef) : WidgetTap

    fun encode(): String = when (this) {
        OpenApp -> APP
        NewEvent -> NEW
        is OpenDay -> "$DAY$SEPARATOR${date.toEpochDay()}"
        is OpenEvent -> "$EVENT$SEPARATOR${ref.encode()}"
    }

    companion object {
        private const val APP = "app"
        private const val NEW = "new"
        private const val DAY = "day"
        private const val EVENT = "event"
        private const val SEPARATOR = ':'

        /** The tap that [encode] wrote, or null for anything else. */
        fun decode(text: String?): WidgetTap? {
            val kind = text?.substringBefore(SEPARATOR)
            val value = text?.substringAfter(SEPARATOR, "").orEmpty()
            return when {
                text == APP -> OpenApp

                text == NEW -> NewEvent

                kind == DAY -> value.toLongOrNull()
                    ?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
                    ?.let(::OpenDay)

                kind == EVENT -> EventRef.decode(value)?.let(::OpenEvent)

                else -> null
            }
        }
    }
}
