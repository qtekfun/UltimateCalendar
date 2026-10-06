// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.chains

import com.qtekfun.ultimatecalendar.data.invitations.AttendedEvents
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.NotificationTags
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RF-07, "changes and cancellations of events you go to": an event the user accepted is moved by
 * its organizer in the provider (written as a sync adapter does). With the option on, the changes
 * channel tells it, from the provider's change alone; with it off, the same change is followed
 * quietly and nothing is shown.
 */
@HiltAndroidTest
class ChangeNotificationChainTest : ChainTest() {
    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var attended: AttendedEvents

    @After
    fun restoreSettings() {
        settings.update { it.copy(notifyChanges = false, notifyCancellations = false) }
    }

    private fun seedAccepted(title: String, start: ZonedDateTime): EventId {
        val id = calendars.seed(
            calendars.timed(
                title,
                calendars.work,
                start,
                attendees = listOf(
                    Attendee.of(BOSS, isOrganizer = true, status = AttendeeStatus.ACCEPTED),
                    Attendee.of(calendars.me, status = AttendeeStatus.ACCEPTED)
                )
            )
        )
        calendars.organizeBy(id, BOSS)
        return id
    }

    private fun followedStart(id: EventId): Long? = runBlocking {
        attended.load().firstOrNull { it.key.eventId == id }
            ?.let { (it.time as EventTime.Timed).start.toEpochMilli() }
    }

    @Test
    fun anAcceptedEventMovedByTheOrganizerIsToldWhenTheOptionIsOn() {
        settings.update { it.copy(notifyChanges = true) }
        startProcess()
        val start = calendars.tomorrowAt(hour = 11)
        val id = seedAccepted(calendars.unique("Planning"), start)
        val tag = NotificationTags.moved(InvitationKey(calendars.work, id))
        // The first check follows it without telling.
        waitUntil(CHECK_TIMEOUT_MS) { followedStart(id) == start.toInstant().toEpochMilli() }
        assertTrue(notifications.activeNotifications.none { it.tag == tag })

        calendars.moveTo(id, calendars.tomorrowAt(hour = 15))

        waitUntil(CHECK_TIMEOUT_MS) { notifications.activeNotifications.any { it.tag == tag } }
    }

    @Test
    fun theSameChangeIsFollowedQuietlyWhenTheOptionIsOff() {
        settings.update { it.copy(notifyChanges = false) }
        startProcess()
        val start = calendars.tomorrowAt(hour = 11)
        val id = seedAccepted(calendars.unique("Planning"), start)
        val tag = NotificationTags.moved(InvitationKey(calendars.work, id))
        waitUntil(CHECK_TIMEOUT_MS) { followedStart(id) == start.toInstant().toEpochMilli() }
        val later = calendars.tomorrowAt(hour = 15)

        calendars.moveTo(id, later)

        // The check ran (it recorded the new time) and still showed nothing.
        waitUntil(CHECK_TIMEOUT_MS) { followedStart(id) == later.toInstant().toEpochMilli() }
        assertTrue(notifications.activeNotifications.none { it.tag == tag })
    }

    private companion object {
        const val BOSS = "boss@example.com"
        const val CHECK_TIMEOUT_MS = 90_000L
    }
}
