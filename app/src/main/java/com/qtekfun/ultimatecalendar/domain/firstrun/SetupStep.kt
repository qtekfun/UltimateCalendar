// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

/** The steps of the first-run wizard (RF-01, RF-08), in the order they are shown. */
enum class SetupStep {
    /** READ_CALENDAR and WRITE_CALENDAR: without them there is nothing to show. */
    CALENDAR_PERMISSION,

    /** Permission granted but no account has calendars: how to add Google or DAVx5. */
    ADD_ACCOUNT,

    NOTIFICATIONS,
    EXACT_ALARMS,
    BATTERY,

    /** The maker's own auto-start switches; no app can read or change them. */
    AUTOSTART,

    /** Other calendar apps that raise their own reminders: how to turn those off. */
    OTHER_CALENDAR_APPS,

    /** A reminder a minute ahead, to see whether this phone delivers on time. */
    TEST_REMINDER
}
