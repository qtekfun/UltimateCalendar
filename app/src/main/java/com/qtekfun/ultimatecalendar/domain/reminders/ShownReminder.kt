// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import java.time.Instant

/** A reminder that was shown: its id and the time it was for, since ids are reused. */
data class ShownReminder(val reminderId: Long, val at: Instant)
