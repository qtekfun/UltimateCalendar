// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val FROM_VERSION = 6
private const val TO_VERSION = 7

/**
 * Version 7 drops `default_calendar`: the default calendar lives in the settings since T20 and
 * nothing has written the table since. Its value is not copied: no build has been released and
 * no build after T20 stores anything there.
 */
val MIGRATION_6_7: Migration = object : Migration(FROM_VERSION, TO_VERSION) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `default_calendar`")
    }
}
