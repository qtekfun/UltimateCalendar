// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.demo

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.reminders.EventReminders
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Invented calendars and events for looking at the UI without touching the phone's calendar
 * provider. Nothing here is real: names, titles and addresses are made up. It lives in the
 * debug source set, so release builds do not contain it.
 */
object DemoData {
    private val google = CalendarAccount("alex.demo@example.org", "com.google")
    private val dav = CalendarAccount("family.demo@example.org", "bitfire.at.davdroid")
    private val device = CalendarAccount("Device", "LOCAL")

    private fun calendar(id: Long, account: CalendarAccount, name: String, color: Long) =
        CalendarInfo(
            id = CalendarId(id),
            account = account,
            displayName = name,
            color = color.toInt(),
            access = CalendarAccess.OWNER
        )

    val personal = calendar(1, google, "Personal", 0xFF1A73E8)
    val work = calendar(2, google, "Work", 0xFF0B8043)
    val birthdays = calendar(3, google, "Birthdays", 0xFFF6BF26)
    val holidays = calendar(4, google, "Holidays", 0xFF8E24AA)
    val family = calendar(5, dav, "Family", 0xFFD50000)
    val gym = calendar(6, dav, "Gym and sport", 0xFFF4511E)
    val local = calendar(7, device, "Reminders", 0xFF039BE5)

    val calendars = listOf(personal, work, birthdays, holidays, family, gym, local)

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val newYork = ZoneId.of("America/New_York")

    /** About 30 events around [today]: all-day, overlapping, repeating, pending, other zones. */
    @Suppress("LongMethod")
    fun instances(today: LocalDate, zone: ZoneId): List<EventInstance> {
        var next = 0L

        fun timed(
            day: Int,
            from: String,
            minutes: Long,
            title: String,
            calendar: CalendarInfo,
            status: AttendeeStatus? = null,
            repeats: Boolean = false,
            place: String? = null,
            eventZone: ZoneId = zone
        ): EventInstance {
            val start = today.plusDays(day.toLong()).atTime(LocalTime.parse(from)).atZone(eventZone)
            return EventInstance(
                eventId = EventId(++next),
                calendarId = calendar.id,
                title = title,
                time = EventTime.Timed(
                    start.toInstant(),
                    start.plusMinutes(minutes).toInstant(),
                    eventZone
                ),
                location = place,
                isRecurring = repeats,
                selfStatus = status
            )
        }

        fun allDay(day: Int, days: Int, title: String, calendar: CalendarInfo) = EventInstance(
            eventId = EventId(++next),
            calendarId = calendar.id,
            title = title,
            time = EventTime.AllDay(
                today.plusDays(day.toLong()),
                today.plusDays((day + days).toLong())
            )
        )

        val pending = AttendeeStatus.NEEDS_ACTION
        return listOf(
            timed(0, "09:00", 30, "Team standup", work, repeats = true),
            timed(0, "09:15", 60, "Design review", work, AttendeeStatus.ACCEPTED, place = "Room 4"),
            timed(0, "09:30", 45, "Quarterly planning sync", work, pending, place = "Video call"),
            timed(0, "12:30", 60, "Lunch with Sam", personal, AttendeeStatus.TENTATIVE),
            timed(0, "15:00", 90, "Dentist", personal, place = "Dr. Lopez, Main St"),
            timed(0, "18:30", 60, "Climbing", gym, repeats = true),
            timed(0, "21:00", 60, "Call with Tokyo office", work, eventZone = tokyo),
            allDay(0, 1, "Maya's birthday", birthdays),
            timed(1, "08:00", 45, "Gym", gym, repeats = true),
            timed(1, "10:00", 120, "Workshop: planning", work, AttendeeStatus.ACCEPTED),
            timed(1, "10:30", 30, "Coffee with Alex", personal, AttendeeStatus.DECLINED),
            timed(1, "14:00", 60, "1:1 with manager", work, repeats = true),
            timed(1, "17:00", 60, "Parent meeting", family, pending, place = "School"),
            allDay(1, 3, "Conference in Lisbon", work),
            timed(2, "11:00", 30, "Pick up the parcel", local),
            timed(2, "13:00", 60, "Lunch and learn", work),
            timed(2, "19:00", 150, "Dinner with the family", family, place = "Grandma's"),
            timed(3, "09:00", 60, "Sprint demo", work, AttendeeStatus.ACCEPTED),
            timed(3, "16:00", 60, "Piano lesson", family, repeats = true),
            timed(3, "22:00", 60, "Webinar from New York", personal, eventZone = newYork),
            allDay(4, 1, "Public holiday", holidays),
            timed(4, "10:00", 90, "Brunch", personal),
            timed(5, "08:30", 60, "Run in the park", gym),
            timed(6, "18:00", 120, "Board game night", personal, place = "Casa Rivera"),
            timed(-1, "09:00", 30, "Team standup", work, repeats = true),
            timed(-1, "13:00", 60, "Retro", work),
            timed(-2, "17:30", 60, "Climbing", gym, repeats = true),
            allDay(-3, 2, "Trip to the coast", personal),
            timed(-4, "11:00", 45, "Vet appointment", family),
            timed(8, "09:00", 60, "Budget review", work, pending),
            timed(9, "15:00", 60, "Haircut", personal)
        )
    }
}

/** A read-only [CalendarSource] over [DemoData], for the demo screens. */
@Suppress("TooManyFunctions")
class DemoCalendarSource(private val zone: ZoneId, private val today: LocalDate) : CalendarSource {
    private val unsupported = CalendarResult.Failure(CalendarError.ReadOnly)

    override val changes: Flow<Unit> = emptyFlow()

    override suspend fun calendars() = CalendarResult.Success(DemoData.calendars)

    override suspend fun instances(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventInstance>> = CalendarResult.Success(
        DemoData.instances(today, zone)
            .filter { calendarIds == null || it.calendarId in calendarIds }
            .filter { it.time.startIn(zone) < range.end && it.time.endIn(zone) > range.start }
            .sortedBy { it.time.startIn(zone) }
    )

    override suspend fun instancesWithReminders(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventReminders>> = CalendarResult.Success(emptyList())

    override suspend fun search(
        query: String,
        calendarIds: Set<CalendarId>?,
        range: TimeRange?
    ): CalendarResult<List<SearchableEvent>> = CalendarResult.Success(emptyList())

    override suspend fun event(id: EventId): CalendarResult<Event> = unsupported

    override suspend fun create(draft: EventDraft): CalendarResult<EventId> = unsupported

    override suspend fun update(event: Event): CalendarResult<Unit> = unsupported

    override suspend fun delete(id: EventId): CalendarResult<Unit> = unsupported

    override suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit> = unsupported

    override suspend fun cancelInstance(id: EventId, originalStart: Instant) = unsupported

    override suspend fun respond(id: EventId, status: AttendeeStatus) = unsupported
}
