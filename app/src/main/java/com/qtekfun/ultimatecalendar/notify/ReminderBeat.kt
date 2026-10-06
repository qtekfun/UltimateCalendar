// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

/**
 * The periodic work that keeps reminders deliverable (RF-08): the heartbeat's alarm and the
 * robust mode's service both run it. It never touches the network.
 */
fun interface ReminderBeat {
    suspend fun beat()
}
