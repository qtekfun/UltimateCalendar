// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val FROM_VERSION = 8
private const val TO_VERSION = 9

/**
 * Version 9 makes an invitation the pair of an event and the address of the user's it invites, so
 * that an event can invite two of the user's accounts: `notified_invitations` and
 * `invitation_re_reminders` get an `address` in their key, and the first one the `account` it is
 * for. Both tables are rebuilt (a key cannot change in place) and every row is kept, with a blank
 * address: the account of its own calendar, which is what they all were.
 */
val MIGRATION_8_9: Migration = object : Migration(FROM_VERSION, TO_VERSION) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `notified_invitations_new` (`calendarId` INTEGER NOT" +
                " NULL, `eventId` INTEGER NOT NULL, `title` TEXT NOT NULL, `allDay` INTEGER" +
                " NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `zone` TEXT," +
                " `location` TEXT," +
                " `organizer` TEXT, `address` TEXT NOT NULL, `account` TEXT," +
                " PRIMARY KEY(`calendarId`, `eventId`, `address`))"
        )
        connection.execSQL(
            "INSERT INTO `notified_invitations_new` SELECT `calendarId`, `eventId`, `title`," +
                " `allDay`, `start`, `end`, `zone`, `location`, `organizer`, '', NULL" +
                " FROM `notified_invitations`"
        )
        connection.execSQL("DROP TABLE `notified_invitations`")
        connection.execSQL(
            "ALTER TABLE `notified_invitations_new` RENAME TO `notified_invitations`"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `invitation_re_reminders_new` (`calendarId` INTEGER NOT" +
                " NULL, `eventId` INTEGER NOT NULL, `moment` TEXT NOT NULL, `start` INTEGER NOT" +
                " NULL, `at` INTEGER NOT NULL, `settled` INTEGER NOT NULL, `address` TEXT NOT" +
                " NULL, PRIMARY KEY(`calendarId`, `eventId`, `address`, `moment`, `start`))"
        )
        connection.execSQL(
            "INSERT INTO `invitation_re_reminders_new` SELECT `calendarId`, `eventId`, `moment`," +
                " `start`, `at`, `settled`, '' FROM `invitation_re_reminders`"
        )
        connection.execSQL("DROP TABLE `invitation_re_reminders`")
        connection.execSQL(
            "ALTER TABLE `invitation_re_reminders_new` RENAME TO `invitation_re_reminders`"
        )
    }
}
