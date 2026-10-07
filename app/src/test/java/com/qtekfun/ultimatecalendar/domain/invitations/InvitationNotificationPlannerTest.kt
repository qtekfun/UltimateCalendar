// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvitationNotificationPlannerTest {
    private val off = ChangeNotifications(changes = false, cancellations = false)
    private val on = ChangeNotifications(changes = true, cancellations = true)

    private fun invitation(id: Long, title: String = "Event $id") = Invitation(
        key = InvitationKey(CalendarId(1), EventId(id)),
        title = title,
        time = EventTime.Timed(
            Instant.parse("2026-06-10T10:00:00Z"),
            Instant.parse("2026-06-10T11:00:00Z"),
            ZoneOffset.UTC
        )
    )

    private fun changes(
        new: List<Invitation> = emptyList(),
        changed: List<InvitationChange> = emptyList(),
        cancelled: List<Invitation> = emptyList(),
        answered: List<Invitation> = emptyList()
    ) = InvitationChanges(new, changed, cancelled, answered)

    @Test
    fun `nothing changed plans nothing`() {
        assertTrue(InvitationNotificationPlanner.plan(changes(), on).isEmpty())
    }

    @Test
    fun `every new invitation is shown once and alerts, in order`() {
        val plan = InvitationNotificationPlanner.plan(
            changes(new = listOf(invitation(1), invitation(2))),
            off
        )
        assertEquals(
            listOf(
                NotificationOp.ShowInvitation(invitation(1), InvitationAlert.NEW),
                NotificationOp.ShowInvitation(invitation(2), InvitationAlert.NEW)
            ),
            plan
        )
    }

    @Test
    fun `a changed invitation alerts again when the changes channel is off`() {
        val change = InvitationChange(invitation(1), invitation(1, "Moved"))
        val plan = InvitationNotificationPlanner.plan(changes(changed = listOf(change)), off)
        assertEquals(
            listOf(NotificationOp.ShowInvitation(invitation(1, "Moved"), InvitationAlert.CHANGED)),
            plan
        )
    }

    @Test
    fun `a changed invitation updates silently and is announced when changes are on`() {
        val change = InvitationChange(invitation(1), invitation(1, "Moved"))
        val plan = InvitationNotificationPlanner.plan(changes(changed = listOf(change)), on)
        assertEquals(
            listOf(
                NotificationOp.ShowInvitation(invitation(1, "Moved"), InvitationAlert.SILENT),
                NotificationOp.ShowMoved(invitation(1, "Moved"))
            ),
            plan
        )
    }

    @Test
    fun `changes on does not turn on cancellations and the other way round`() {
        val gone = invitation(3)
        val change = InvitationChange(invitation(1), invitation(1, "Moved"))
        val onlyChanges = InvitationNotificationPlanner.plan(
            changes(changed = listOf(change), cancelled = listOf(gone)),
            ChangeNotifications(changes = true, cancellations = false)
        )
        assertTrue(onlyChanges.any { it is NotificationOp.ShowMoved })
        assertFalse(onlyChanges.any { it is NotificationOp.ShowCancelled })

        val onlyCancellations = InvitationNotificationPlanner.plan(
            changes(changed = listOf(change), cancelled = listOf(gone)),
            ChangeNotifications(changes = false, cancellations = true)
        )
        assertFalse(onlyCancellations.any { it is NotificationOp.ShowMoved })
        assertTrue(onlyCancellations.contains(NotificationOp.ShowCancelled(gone)))
    }

    @Test
    fun `a cancelled event removes its notification and says so only if asked`() {
        val gone = invitation(3)
        assertEquals(
            listOf(NotificationOp.CancelInvitation(gone.key)),
            InvitationNotificationPlanner.plan(changes(cancelled = listOf(gone)), off)
        )
        assertEquals(
            listOf(
                NotificationOp.CancelInvitation(gone.key),
                NotificationOp.ShowCancelled(gone)
            ),
            InvitationNotificationPlanner.plan(changes(cancelled = listOf(gone)), on)
        )
    }

    @Test
    fun `an invitation answered elsewhere only loses its notification, whatever the settings`() {
        val answered = invitation(4)
        for (settings in listOf(off, on)) {
            assertEquals(
                listOf(NotificationOp.CancelInvitation(answered.key)),
                InvitationNotificationPlanner.plan(changes(answered = listOf(answered)), settings)
            )
        }
    }

    private fun attended(
        changed: List<InvitationChange> = emptyList(),
        cancelled: List<Invitation> = emptyList(),
        dropped: List<InvitationKey> = emptyList()
    ) = changes().withAttended(AttendedChanges(changed, cancelled, dropped))

    @Test
    fun `a moved event the user goes to is announced only when changes are on`() {
        val change = InvitationChange(invitation(1), invitation(1, "Moved"))

        assertTrue(
            InvitationNotificationPlanner.plan(attended(changed = listOf(change)), off).isEmpty()
        )
        assertEquals(
            listOf(NotificationOp.ShowMoved(invitation(1, "Moved"))),
            InvitationNotificationPlanner.plan(
                attended(changed = listOf(change)),
                ChangeNotifications(changes = true, cancellations = false)
            )
        )
        assertTrue(
            InvitationNotificationPlanner.plan(
                attended(changed = listOf(change)),
                ChangeNotifications(changes = false, cancellations = true)
            ).isEmpty()
        )
    }

    @Test
    fun `a cancelled event the user goes to clears its notes and says so only if asked`() {
        val gone = invitation(3)

        assertEquals(
            listOf(NotificationOp.ClearChanges(gone.key)),
            InvitationNotificationPlanner.plan(attended(cancelled = listOf(gone)), off)
        )
        assertEquals(
            listOf(NotificationOp.ClearChanges(gone.key), NotificationOp.ShowCancelled(gone)),
            InvitationNotificationPlanner.plan(attended(cancelled = listOf(gone)), on)
        )
    }

    @Test
    fun `an event no longer followed only loses its notes, whatever the settings`() {
        val key = invitation(5).key
        for (settings in listOf(off, on)) {
            assertEquals(
                listOf(NotificationOp.ClearChanges(key)),
                InvitationNotificationPlanner.plan(attended(dropped = listOf(key)), settings)
            )
        }
    }

    @Test
    fun `attended changes make the changes not empty and keep the invitation ones`() {
        val base = changes(new = listOf(invitation(1)))
        val key = invitation(2).key

        val both = base.withAttended(AttendedChanges(emptyList(), emptyList(), listOf(key)))

        assertEquals(base.new, both.new)
        assertEquals(listOf(key), both.attendedDropped)
        assertFalse(both.isEmpty)
        assertTrue(attended().isEmpty)
        assertFalse(attended(cancelled = listOf(invitation(3))).isEmpty)
        assertFalse(
            attended(changed = listOf(InvitationChange(invitation(1), invitation(1)))).isEmpty
        )
    }

    @Test
    fun `all categories together keep new first and cancellations last`() {
        val plan = InvitationNotificationPlanner.plan(
            changes(
                new = listOf(invitation(1)),
                changed = listOf(InvitationChange(invitation(2), invitation(2, "B"))),
                cancelled = listOf(invitation(3)),
                answered = listOf(invitation(4))
            ),
            off
        )
        assertEquals(
            listOf(
                NotificationOp.ShowInvitation(invitation(1), InvitationAlert.NEW),
                NotificationOp.ShowInvitation(invitation(2, "B"), InvitationAlert.CHANGED),
                NotificationOp.CancelInvitation(invitation(3).key),
                NotificationOp.CancelInvitation(invitation(4).key)
            ),
            plan
        )
    }

    @Test
    fun `a summary appears from two notifications and goes with the last one`() {
        assertEquals(SummaryOp.Cancel, InvitationNotificationPlanner.summary(0))
        assertEquals(SummaryOp.Cancel, InvitationNotificationPlanner.summary(1))
        assertEquals(SummaryOp.Show(2), InvitationNotificationPlanner.summary(2))
        assertEquals(SummaryOp.Show(7), InvitationNotificationPlanner.summary(7))
    }

    @Test
    fun `tags tell kinds and events apart and recognise the group members`() {
        val a = InvitationKey(CalendarId(1), EventId(23))
        val b = InvitationKey(CalendarId(12), EventId(3))
        assertNotEquals(NotificationTags.invitation(a), NotificationTags.invitation(b))
        assertNotEquals(NotificationTags.invitation(a), NotificationTags.moved(a))
        assertNotEquals(NotificationTags.moved(a), NotificationTags.cancelled(a))
        assertTrue(NotificationTags.isInvitation(NotificationTags.invitation(a)))
        assertFalse(NotificationTags.isInvitation(NotificationTags.moved(a)))
        assertFalse(NotificationTags.isInvitation(NotificationTags.SUMMARY))
        assertFalse(NotificationTags.isInvitation(null))
    }

    @Test
    fun `the invitations of two accounts of one event have their own tags without the address`() {
        val own = InvitationKey(CalendarId(1), EventId(23))
        val b = InvitationKey(CalendarId(1), EventId(23), "b@gmail.com")
        val c = InvitationKey(CalendarId(1), EventId(23), "c@gmail.com")

        assertEquals(3, setOf(own, b, c).map { NotificationTags.invitation(it) }.toSet().size)
        assertFalse(NotificationTags.invitation(b).contains("gmail"))
        assertFalse(NotificationTags.moved(b).contains("@"))
        assertEquals(NotificationTags.invitation(b), NotificationTags.invitation(b.copy()))
        assertTrue(NotificationTags.isInvitation(NotificationTags.invitation(b)))
    }

    @Test
    fun `answers map to the attendee status the source stores`() {
        assertEquals(AttendeeStatus.ACCEPTED, InvitationAnswer.ACCEPT.status)
        assertEquals(AttendeeStatus.TENTATIVE, InvitationAnswer.MAYBE.status)
        assertEquals(AttendeeStatus.DECLINED, InvitationAnswer.DECLINE.status)
    }
}
