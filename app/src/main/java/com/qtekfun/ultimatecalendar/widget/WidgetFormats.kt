// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.content.Context
import android.text.format.DateUtils
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaSlot
import com.qtekfun.ultimatecalendar.domain.widget.AgendaWidgetRow
import com.qtekfun.ultimatecalendar.domain.widget.MonthWidgetCell
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * The texts of the widgets, in the device's language, zone and 12/24 hour setting: the system's
 * own date formatting, so every locale reads right.
 */
internal class WidgetFormats(private val context: Context, private val zone: ZoneId) {
    private val locale: Locale = Locale.getDefault()

    /** "Tue, Oct 6". */
    fun shortDate(date: LocalDate): String = DateUtils.formatDateTime(
        context,
        noon(date),
        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
    )

    /** "Tuesday, October 6, 2026", for a screen reader. */
    fun longDate(date: LocalDate): String = DateUtils.formatDateTime(
        context,
        noon(date),
        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
    )

    /** "October 2026". */
    fun month(month: YearMonth): String = DateUtils.formatDateTime(
        context,
        noon(month.atDay(1)),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_MONTH_DAY or DateUtils.FORMAT_SHOW_YEAR
    )

    fun weekday(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, locale)

    /** The header of a day of the list: "Today, Tue, Oct 6", "Tomorrow, Wed, Oct 7" or the date. */
    fun dayLabel(day: AgendaWidgetRow.Day): String {
        val date = shortDate(day.date)
        return when {
            day.isToday -> context.getString(R.string.cal_day_today, date)
            day.isTomorrow -> context.getString(R.string.widget_tomorrow, date)
            else -> date
        }
    }

    /** What an event's time says on one day of the list. */
    fun time(slot: AgendaSlot): String = when (slot) {
        AgendaSlot.AllDay -> context.getString(R.string.cal_all_day)

        is AgendaSlot.Span -> context.getString(
            R.string.month_time_range,
            clock(slot.start),
            clock(slot.end)
        )

        is AgendaSlot.From -> clock(slot.start)

        is AgendaSlot.Until -> context.getString(R.string.agenda_until, clock(slot.end))
    }

    /** What a screen reader says of a day cell of the month. */
    fun cellDescription(cell: MonthWidgetCell): String {
        val date = longDate(cell.date)
        val dated = if (cell.isToday) context.getString(R.string.cal_day_today, date) else date
        val events = if (cell.eventCount == 0) {
            context.getString(R.string.month_day_no_events, dated)
        } else {
            context.resources.getQuantityString(
                R.plurals.month_day_events,
                cell.eventCount,
                dated,
                cell.eventCount
            )
        }
        return if (cell.hasPending) {
            "$events, ${context.getString(R.string.cal_status_pending)}"
        } else {
            events
        }
    }

    private fun clock(instant: Instant): String =
        DateUtils.formatDateTime(context, instant.toEpochMilli(), DateUtils.FORMAT_SHOW_TIME)

    private fun noon(date: LocalDate): Long =
        date.atTime(NOON, 0).atZone(zone).toInstant().toEpochMilli()

    private companion object {
        const val NOON = 12
    }
}
