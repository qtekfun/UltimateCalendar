// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.screenshots

import android.provider.CalendarContract.Attendees
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The invented week of the screenshots: a team's Monday to Friday, a birthday, a trip, a weekend,
 * a repeating event and two invitations from invented people. [monday] is the Monday of "this
 * week"; [spanish] picks the language of the titles.
 */
class DemoWeek(private val monday: LocalDate, private val spanish: Boolean) {
    private fun t(english: String, spanish: String) = if (this.spanish) spanish else english

    private fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(monday.plusDays(day.toLong()), LocalTime.of(hour, minute))

    private val maya =
        DemoGuest("Maya Chen", "maya.chen@example.com", Attendees.ATTENDEE_STATUS_ACCEPTED)
    private val liam =
        DemoGuest("Liam Novak", "liam.novak@example.com", Attendees.ATTENDEE_STATUS_ACCEPTED)

    val calendars = listOf(
        DemoCalendar(WORK, t("Work", "Trabajo"), WORK_COLOR),
        DemoCalendar(PERSONAL, "Personal", PERSONAL_COLOR),
        DemoCalendar(FAMILY, t("Family", "Familia"), FAMILY_COLOR),
        DemoCalendar(BIRTHDAYS, t("Birthdays", "Cumpleaños"), BIRTHDAYS_COLOR),
        DemoCalendar(TRIPS, t("Trips", "Viajes"), TRIPS_COLOR)
    )

    /** The title of the event whose detail (with attendees) is shown. */
    val detailTitle = t("Product demo", "Demo del producto")

    /** An event near the top of every view, to know that one has loaded. */
    val earlyTitle = t("1:1 with Sam", "1:1 con Sam")

    /** Titles of the two invitations, in the order they are loaded. */
    val firstInvitationTitle = t("Roadmap workshop", "Taller de hoja de ruta")
    val secondInvitationTitle = t("Board game night", "Noche de juegos de mesa")

    /** Everything but the second invitation, which arrives after the notification is taken. */
    val events: List<DemoEvent> = mondayEvents() + tuesday() + wednesday() + thursday() + friday() +
        weekend() + listOf(firstInvitation())

    val secondInvitation = DemoEvent(
        calendar = FAMILY,
        title = secondInvitationTitle,
        start = at(SATURDAY, 19, 30),
        minutes = 210,
        location = t("Liam's place", "Casa de Liam"),
        organizer = liam,
        me = Attendees.ATTENDEE_STATUS_INVITED
    )

    private fun mondayEvents() = listOf(
        DemoEvent(
            WORK,
            t("Team standup", "Daily del equipo"),
            // Started two weeks ago, so the month before today is not empty.
            at(-14, 9, 30),
            minutes = 15,
            rrule = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
            location = t("Meeting room 2", "Sala 2")
        ),
        DemoEvent(WORK, t("Sprint planning", "Planificación del sprint"), at(0, 10), 60),
        DemoEvent(
            PERSONAL,
            t("Lunch with Priya", "Comida con Priya"),
            at(0, 13),
            60,
            location = "Casa Luna"
        ),
        DemoEvent(WORK, t("Design review", "Revisión de diseño"), at(0, 14), 90),
        DemoEvent(WORK, t("Client call", "Llamada con el cliente"), at(0, 14, 30), 45)
    )

    private fun tuesday() = listOf(
        DemoEvent(WORK, earlyTitle, at(1, 11), 30),
        DemoEvent(
            PERSONAL,
            t("Yoga class", "Clase de yoga"),
            at(-6, 19),
            60,
            rrule = "FREQ=WEEKLY;BYDAY=TU"
        )
    )

    private fun wednesday() = listOf(
        DemoEvent(
            WORK,
            detailTitle,
            at(2, 11),
            60,
            location = t("Main hall", "Sala principal"),
            description = t(
                "Show the new onboarding flow to the whole team.",
                "Enseñar el nuevo flujo de bienvenida a todo el equipo."
            ),
            organizer = maya,
            me = Attendees.ATTENDEE_STATUS_ACCEPTED,
            guests = listOf(
                liam,
                DemoGuest(
                    "Sofia Rossi",
                    "sofia.rossi@example.com",
                    Attendees.ATTENDEE_STATUS_ACCEPTED
                ),
                DemoGuest(
                    "Noah Park",
                    "noah.park@example.com",
                    Attendees.ATTENDEE_STATUS_TENTATIVE
                ),
                DemoGuest(
                    "Ava Martin",
                    "ava.martin@example.com",
                    Attendees.ATTENDEE_STATUS_DECLINED
                ),
                DemoGuest(
                    "Omar Haddad",
                    "omar.haddad@example.com",
                    Attendees.ATTENDEE_STATUS_INVITED
                )
            )
        ),
        DemoEvent(WORK, t("Team lunch", "Comida de equipo"), at(2, 13), 75),
        DemoEvent(PERSONAL, t("Dentist", "Dentista"), at(2, 17, 30), 45, color = DENTIST_COLOR)
    )

    private fun thursday() = listOf(
        DemoEvent(WORK, t("Release planning", "Planificación de la versión"), at(3, 10), 90),
        DemoEvent(
            BIRTHDAYS,
            t("Mia's birthday", "Cumpleaños de Mia"),
            allDay = monday.plusDays(3)
        ),
        DemoEvent(
            FAMILY,
            t("Dinner at Casa Luna", "Cena en Casa Luna"),
            at(3, 20),
            120,
            location = "Casa Luna"
        )
    )

    private fun friday() = listOf(
        DemoEvent(WORK, t("Retrospective", "Retrospectiva"), at(4, 16), 60),
        DemoEvent(
            TRIPS,
            t("Trip to Lisbon", "Viaje a Lisboa"),
            allDay = monday.plusDays(TRIP_START_DAY),
            days = TRIP_DAYS
        )
    )

    private fun weekend() = listOf(
        DemoEvent(
            PERSONAL,
            t("Hike in the hills", "Excursión a la sierra"),
            at(SATURDAY, 9),
            360,
            location = t("Trailhead parking", "Aparcamiento del sendero")
        )
    )

    private fun firstInvitation() = DemoEvent(
        calendar = WORK,
        title = firstInvitationTitle,
        start = at(4, 10, 30),
        minutes = 90,
        location = t("Room 4B", "Sala 4B"),
        organizer = maya,
        me = Attendees.ATTENDEE_STATUS_INVITED
    )

    private companion object {
        const val WORK = "work"
        const val PERSONAL = "personal"
        const val FAMILY = "family"
        const val BIRTHDAYS = "birthdays"
        const val TRIPS = "trips"
        const val SATURDAY = 5
        const val TRIP_START_DAY = 10L
        const val TRIP_DAYS = 4
        const val WORK_COLOR = 0xFF1A73E8.toInt()
        const val PERSONAL_COLOR = 0xFF0B8043.toInt()
        const val FAMILY_COLOR = 0xFFF4511E.toInt()
        const val BIRTHDAYS_COLOR = 0xFFD81B60.toInt()
        const val TRIPS_COLOR = 0xFF8E24AA.toInt()
        const val DENTIST_COLOR = 0xFFE67C73.toInt()
    }
}
