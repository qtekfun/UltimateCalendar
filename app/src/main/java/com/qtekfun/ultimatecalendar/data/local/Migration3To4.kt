// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val FROM = 3
private const val TO = 4

/** Version 4 (T40) adds the re-reminders of unanswered invitations; nothing existing changes. */
val MIGRATION_3_4: Migration = object : Migration(FROM, TO) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `invitation_re_reminders` (" +
                "`calendarId` INTEGER NOT NULL, `eventId` INTEGER NOT NULL, " +
                "`moment` TEXT NOT NULL, `start` INTEGER NOT NULL, `at` INTEGER NOT NULL, " +
                "`settled` INTEGER NOT NULL, " +
                "PRIMARY KEY(`calendarId`, `eventId`, `moment`, `start`))"
        )
    }
}
