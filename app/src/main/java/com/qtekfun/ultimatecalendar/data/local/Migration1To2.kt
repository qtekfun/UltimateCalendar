// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Version 2 (T08) adds the invitations already notified; nothing existing changes. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `notified_invitations` (" +
                "`calendarId` INTEGER NOT NULL, `eventId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `allDay` INTEGER NOT NULL, " +
                "`start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `zone` TEXT, " +
                "`location` TEXT, `organizer` TEXT, " +
                "PRIMARY KEY(`calendarId`, `eventId`))"
        )
    }
}
