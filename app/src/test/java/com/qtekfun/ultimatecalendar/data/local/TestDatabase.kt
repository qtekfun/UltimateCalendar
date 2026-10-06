// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.mockk.every
import io.mockk.mockk
import kotlin.coroutines.CoroutineContext

/**
 * In-memory database on the host JVM, using the bundled SQLite build for JVM. With a
 * [queryContext] queries run in it instead of on Room's own threads, so a test that drives a
 * background collector on a test dispatcher stays deterministic.
 */
fun inMemoryDatabase(queryContext: CoroutineContext? = null): UltimateCalendarDatabase {
    val context = mockk<Context>(relaxed = true)
    every { context.applicationContext } returns context
    val builder = Room.inMemoryDatabaseBuilder<UltimateCalendarDatabase>(context)
        .setDriver(BundledSQLiteDriver())
    return (queryContext?.let { builder.setQueryCoroutineContext(it) } ?: builder).build()
}
