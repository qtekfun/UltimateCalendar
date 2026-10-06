// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.domain.invitations.AttendedChanges
import com.qtekfun.ultimatecalendar.domain.invitations.ChangeNotifications
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAlert
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationChange
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationChanges
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemInvitationNotifierTest {
    private val surface = RecordingSurface()
    private var options = ChangeNotifications(changes = false, cancellations = false)
    private val notifier = SystemInvitationNotifier(surface) { options }

    private fun changes(
        new: List<Long> = emptyList(),
        changed: List<Long> = emptyList(),
        cancelled: List<Long> = emptyList(),
        answered: List<Long> = emptyList()
    ) = InvitationChanges(
        new = new.map { sampleInvitation(it) },
        changed = changed.map {
            InvitationChange(sampleInvitation(it), sampleInvitation(it, "New"))
        },
        cancelled = cancelled.map { sampleInvitation(it) },
        answeredElsewhere = answered.map { sampleInvitation(it) }
    )

    @Test
    fun `nothing to tell touches nothing, not even the summary`() = runTest {
        notifier.notify(changes())
        assertTrue(surface.calls.isEmpty())
    }

    @Test
    fun `new invitations are shown one by one and the summary is refreshed once at the end`() =
        runTest {
            notifier.notify(changes(new = listOf(1, 2, 3)))
            assertEquals(listOf("show 1", "show 2", "show 3", "summary"), surface.calls)
        }

    @Test
    fun `the same changes twice give the same calls, so a replay replaces and never piles up`() =
        runTest {
            notifier.notify(changes(new = listOf(1), answered = listOf(2)))
            val first = surface.calls.toList()
            surface.calls.clear()
            notifier.notify(changes(new = listOf(1), answered = listOf(2)))
            assertEquals(first, surface.calls)
        }

    @Test
    fun `an answer given elsewhere or a cancelled event clears the notification`() = runTest {
        notifier.notify(changes(cancelled = listOf(5), answered = listOf(6)))
        assertEquals(listOf("cancel 5", "cancel 6", "summary"), surface.calls)
    }

    @Test
    fun `optional notifications follow the settings read at the moment of notifying`() = runTest {
        val all = changes(changed = listOf(1), cancelled = listOf(2))
        notifier.notify(all)
        assertEquals(listOf("show 1", "cancel 2", "summary"), surface.calls)
        assertEquals(InvitationAlert.CHANGED, surface.alerts.values.single())

        surface.calls.clear()
        options = ChangeNotifications(changes = true, cancellations = true)
        notifier.notify(all)
        assertEquals(
            listOf("show 1", "moved 1", "cancel 2", "cancelled 2", "summary"),
            surface.calls
        )
        assertEquals(InvitationAlert.SILENT, surface.alerts.values.single())
    }

    @Test
    fun `events the user goes to are told on the changes channel only when asked`() = runTest {
        val attended = changes().withAttended(
            AttendedChanges(
                changed = listOf(InvitationChange(sampleInvitation(1), sampleInvitation(1, "New"))),
                cancelled = listOf(sampleInvitation(2)),
                dropped = listOf(sampleInvitation(3).key)
            )
        )
        notifier.notify(attended)
        assertEquals(listOf("clear 2", "clear 3", "summary"), surface.calls)

        surface.calls.clear()
        options = ChangeNotifications(changes = true, cancellations = true)
        notifier.notify(attended)
        assertEquals(
            listOf("moved 1", "clear 2", "cancelled 2", "clear 3", "summary"),
            surface.calls
        )
    }
}
