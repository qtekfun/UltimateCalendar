// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val FROM_VERSION = 2
private const val TO_VERSION = 3

/**
 * Version 3 (T34) adds the CalDAV account, its calendars and events (the local source of truth)
 * and the queue of changes waiting for the server; nothing existing changes.
 */
val MIGRATION_2_3: Migration = object : Migration(FROM_VERSION, TO_VERSION) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.createAccount()
        connection.createCalendar()
        connection.createEvent()
        connection.createQueue()
    }

    private fun SQLiteConnection.createAccount() {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `dav_account` (`id` INTEGER PRIMARY KEY" +
                " AUTOINCREMENT NOT NULL," +
                " `serverUrl` TEXT NOT NULL, `loginName` TEXT NOT NULL," +
                " `calendarHome` TEXT)"
        )
        execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_dav_account_serverUrl_loginName` ON" +
                " `dav_account` (`serverUrl`," +
                " `loginName`)"
        )
    }

    private fun SQLiteConnection.createCalendar() {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `dav_calendar` (`id` INTEGER PRIMARY KEY" +
                " AUTOINCREMENT NOT NULL," +
                " `accountId` INTEGER NOT NULL, `href` TEXT NOT NULL," +
                " `name` TEXT NOT NULL, `color` TEXT, `sortOrder` INTEGER," +
                " `writable` INTEGER NOT NULL, `syncToken` TEXT," +
                " `ctag` TEXT, FOREIGN KEY(`accountId`) REFERENCES `dav_account`(`id`) ON" +
                " UPDATE NO ACTION ON DELETE CASCADE )"
        )
        execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_dav_calendar_accountId_href` ON" +
                " `dav_calendar` (`accountId`," +
                " `href`)"
        )
    }

    private fun SQLiteConnection.createEvent() {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `dav_event` (`id` INTEGER PRIMARY KEY" +
                " AUTOINCREMENT NOT NULL," +
                " `accountId` INTEGER NOT NULL, `calendarId` INTEGER NOT NULL," +
                " `href` TEXT NOT NULL, `uid` TEXT NOT NULL, `etag` TEXT, `ics` TEXT," +
                " `title` TEXT NOT NULL, `allDay` INTEGER NOT NULL," +
                " `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `zone` TEXT," +
                " `windowStart` INTEGER NOT NULL, `windowEnd` INTEGER, `location` TEXT," +
                " `description` TEXT, `color` INTEGER, `availability` TEXT NOT NULL," +
                " `status` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `rrule` TEXT," +
                " `organizer` TEXT, `modifiedAt` INTEGER," +
                " `attendees` TEXT NOT NULL DEFAULT '[]'," +
                " `reminders` TEXT NOT NULL DEFAULT '[]'," +
                " `exDates` TEXT NOT NULL DEFAULT '[]'," +
                " `rDates` TEXT NOT NULL DEFAULT '[]'," +
                " `overrides` TEXT NOT NULL DEFAULT '[]'," +
                " `masterRecurrenceId` TEXT DEFAULT NULL, `dirtyFields` INTEGER NOT NULL," +
                " `deleted` INTEGER NOT NULL, `conflictTitle` TEXT DEFAULT NULL," +
                " `conflictDescription` TEXT DEFAULT NULL," +
                " `conflictLocation` TEXT DEFAULT NULL," +
                " `deletedOnServer` INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(`calendarId`)" +
                " REFERENCES `dav_calendar`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_dav_event_accountId_href` ON" +
                " `dav_event` (`accountId`," +
                " `href`)"
        )
        execSQL(
            "CREATE INDEX IF NOT EXISTS `index_dav_event_calendarId_windowStart` ON" +
                " `dav_event` (`calendarId`," +
                " `windowStart`)"
        )
        execSQL(
            "CREATE INDEX IF NOT EXISTS `index_dav_event_accountId_uid` ON `dav_event`" +
                " (`accountId`," +
                " `uid`)"
        )
    }

    private fun SQLiteConnection.createQueue() {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_operation` (`id` INTEGER PRIMARY KEY" +
                " AUTOINCREMENT NOT NULL," +
                " `accountId` INTEGER NOT NULL, `type` TEXT NOT NULL," +
                " `eventId` INTEGER NOT NULL, `payload` TEXT NOT NULL, `slot` TEXT," +
                " `createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL," +
                " `nextAttemptAt` INTEGER NOT NULL, `lastError` TEXT," +
                " `failed` INTEGER NOT NULL," +
                " `startedAt` INTEGER, FOREIGN KEY(`accountId`) REFERENCES `dav_account`(`id`)" +
                " ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pending_operation_accountId_nextAttemptAt`" +
                " ON `pending_operation` (`accountId`," +
                " `nextAttemptAt`)"
        )
        execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pending_operation_accountId_eventId` ON" +
                " `pending_operation` (`accountId`," +
                " `eventId`)"
        )
    }
}
