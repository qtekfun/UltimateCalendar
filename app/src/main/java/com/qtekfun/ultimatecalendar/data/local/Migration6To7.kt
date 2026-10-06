// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val FROM_VERSION = 6
private const val TO_VERSION = 7

/**
 * Version 7 adds the record of the upcoming events the user goes to, to tell their changes and
 * cancellations (RF-07); nothing existing changes.
 */
val MIGRATION_6_7: Migration = object : Migration(FROM_VERSION, TO_VERSION) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `attended_events` (`calendarId` INTEGER NOT NULL, " +
                "`eventId` INTEGER NOT NULL, `title` TEXT NOT NULL, `allDay` INTEGER NOT NULL, " +
                "`start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `zone` TEXT, " +
                "`placeHash` TEXT NOT NULL, `ownEdit` INTEGER NOT NULL, " +
                "PRIMARY KEY(`calendarId`, `eventId`))"
        )
    }
}
