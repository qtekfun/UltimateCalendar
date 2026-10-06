// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val FROM_VERSION = 5
private const val TO_VERSION = 6

/** Version 6 (T39) adds the ICS subscriptions and their events; nothing existing changes. */
val MIGRATION_5_6: Migration = object : Migration(FROM_VERSION, TO_VERSION) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.createSubscription()
        connection.createEvent()
    }

    private fun SQLiteConnection.createSubscription() {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `subscription` (`id` INTEGER PRIMARY KEY AUTOINCREMENT" +
                " NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL," +
                " `enabled` INTEGER NOT NULL, `refreshHours` INTEGER NOT NULL," +
                " `host` TEXT NOT NULL, `urlKey` TEXT NOT NULL, `urlSecret` TEXT NOT NULL," +
                " `etag` TEXT, `lastModified` TEXT, `lastAttemptAt` INTEGER," +
                " `lastSuccessAt` INTEGER, `error` TEXT, `errorCode` INTEGER," +
                " `eventCount` INTEGER NOT NULL, `skipped` INTEGER NOT NULL)"
        )
        execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_subscription_urlKey` ON" +
                " `subscription` (`urlKey`)"
        )
    }

    private fun SQLiteConnection.createEvent() {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `subscription_event` (`id` INTEGER PRIMARY KEY" +
                " AUTOINCREMENT NOT NULL, `subscriptionId` INTEGER NOT NULL," +
                " `uid` TEXT NOT NULL, `title` TEXT NOT NULL, `allDay` INTEGER NOT NULL," +
                " `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `zone` TEXT," +
                " `windowStart` INTEGER NOT NULL, `windowEnd` INTEGER, `location` TEXT," +
                " `description` TEXT, `availability` TEXT NOT NULL, `status` TEXT NOT NULL," +
                " `sequence` INTEGER NOT NULL, `rrule` TEXT, `organizer` TEXT," +
                " `exDates` TEXT NOT NULL DEFAULT '[]', `rDates` TEXT NOT NULL DEFAULT '[]'," +
                " `overrides` TEXT NOT NULL DEFAULT '[]'," +
                " `masterRecurrenceId` TEXT DEFAULT NULL," +
                " FOREIGN KEY(`subscriptionId`) REFERENCES `subscription`(`id`) ON UPDATE" +
                " NO ACTION ON DELETE CASCADE )"
        )
        execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_subscription_event_subscriptionId_uid`" +
                " ON `subscription_event` (`subscriptionId`, `uid`)"
        )
        execSQL(
            "CREATE INDEX IF NOT EXISTS `index_subscription_event_subscriptionId_windowStart`" +
                " ON `subscription_event` (`subscriptionId`, `windowStart`)"
        )
    }
}
