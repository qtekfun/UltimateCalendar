// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.agenda

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaDays
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaItem
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaItems
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.ui.adaptive.AdaptivePreviews
import com.qtekfun.ultimatecalendar.ui.adaptive.currentAdaptiveLayout
import com.qtekfun.ultimatecalendar.ui.components.ComponentPreviews
import com.qtekfun.ultimatecalendar.ui.components.PreviewSurface
import com.qtekfun.ultimatecalendar.ui.components.PreviewToday
import com.qtekfun.ultimatecalendar.ui.detail.InvitationDetailPreviewContent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// Invented data only.
private val madrid = ZoneId.of("Europe/Madrid")
private val tokyo = ZoneId.of("Asia/Tokyo")
private const val BLUE = 0xFF3F51B5.toInt()
private const val GREEN = 0xFF0B8043.toInt()
private const val ORANGE = 0xFFF4511E.toInt()

private var lastId = 0L

private fun timed(
    title: String,
    from: String,
    to: String,
    color: Int,
    location: String? = null,
    status: AttendeeStatus? = null,
    zone: ZoneId = madrid
) = EventInstance(
    eventId = EventId(++lastId),
    calendarId = CalendarId(1),
    title = title,
    time = EventTime.Timed(
        LocalDateTime.parse(from).atZone(madrid).toInstant(),
        LocalDateTime.parse(to).atZone(madrid).toInstant(),
        zone
    ),
    location = location,
    color = color,
    selfStatus = status
)

private fun previewState(): AgendaState {
    val range = DateRange(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-12-01"))
    val trip = EventTime.AllDay(PreviewToday, LocalDate.parse("2026-10-08"))
    val instances = listOf(
        EventInstance(EventId(++lastId), CalendarId(1), "Sample trip", trip, color = GREEN),
        timed("Planning", "2026-10-06T09:00", "2026-10-06T10:30", BLUE, "Meeting room 2"),
        timed("Coffee", "2026-10-06T12:00", "2026-10-06T12:45", ORANGE),
        timed(
            "Invitation to a talk",
            "2026-10-07T11:00",
            "2026-10-07T12:00",
            BLUE,
            status = AttendeeStatus.NEEDS_ACTION
        ),
        timed(
            "Maybe: lunch",
            "2026-10-07T13:00",
            "2026-10-07T14:00",
            GREEN,
            "Sample cafe",
            AttendeeStatus.TENTATIVE
        ),
        timed(
            "Call with another office",
            "2026-10-09T03:00",
            "2026-10-09T04:00",
            BLUE,
            zone = tokyo
        ),
        timed("Night shift", "2026-10-10T22:00", "2026-10-11T06:00", ORANGE),
        timed("Monthly review", "2026-11-05T10:00", "2026-11-05T11:00", BLUE, "Sample office")
    )
    val days = AgendaDays.build(range, madrid, instances)
    val items = AgendaItems.flatten(days)
    return AgendaState(PreviewToday, 1, range, madrid, items, AgendaStatus.READY)
}

@ComponentPreviews
@Composable
internal fun AgendaListPreview() {
    PreviewSurface {
        AgendaList(rememberLazyListState(), previewState(), PreviewToday, onOpenEvent = {})
    }
}

/** The Agenda on a phone, a 7" and a 10" tablet and a phone in landscape: two panes when wide. */
@AdaptivePreviews
@Composable
internal fun AgendaAdaptivePreview() {
    val state = previewState()
    val first = state.items.filterIsInstance<AgendaItem.EventRow>().first().entry.instance
    val selected = EventRef.of(first)
    val list = @Composable { modifier: Modifier ->
        AgendaList(
            rememberLazyListState(),
            state,
            PreviewToday,
            onOpenEvent = {},
            selected = selected,
            modifier = modifier
        )
    }
    PreviewSurface {
        if (currentAdaptiveLayout().agendaTwoPane) {
            AgendaMasterDetail(
                selected,
                list,
                detail = { InvitationDetailPreviewContent() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            list(Modifier)
        }
    }
}

/** The two-pane Agenda before anything is picked. */
@Preview(name = "Tablet 10in, nothing selected", widthDp = 1280, heightDp = 800)
@Composable
internal fun AgendaNothingSelectedPreview() {
    val state = previewState()
    PreviewSurface {
        AgendaMasterDetail(
            selected = null,
            list = { AgendaList(rememberLazyListState(), state, PreviewToday, {}, modifier = it) },
            detail = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
