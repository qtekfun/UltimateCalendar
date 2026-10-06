// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus

/** The three ways to answer an invitation (RF-06, RF-07): Accept, Maybe, Decline. */
enum class InvitationAnswer(val status: AttendeeStatus) {
    ACCEPT(AttendeeStatus.ACCEPTED),
    MAYBE(AttendeeStatus.TENTATIVE),
    DECLINE(AttendeeStatus.DECLINED)
}
