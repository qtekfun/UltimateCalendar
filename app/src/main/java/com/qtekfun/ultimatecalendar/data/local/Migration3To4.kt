// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val FROM_VERSION = 3
private const val TO_VERSION = 4

/**
 * Version 4 (T36) keeps what discovery learns about the CalDAV account: the user's calendar
 * addresses (to know which attendee is "me") and whether the server schedules invitations.
 */
val MIGRATION_3_4: Migration = object : Migration(FROM_VERSION, TO_VERSION) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `dav_account` ADD COLUMN `userAddresses` TEXT NOT NULL DEFAULT ''"
        )
        connection.execSQL(
            "ALTER TABLE `dav_account` ADD COLUMN `scheduling` INTEGER NOT NULL DEFAULT 0"
        )
    }
}
