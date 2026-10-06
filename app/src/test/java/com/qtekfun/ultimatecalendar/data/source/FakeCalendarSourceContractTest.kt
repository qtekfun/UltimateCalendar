// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** The fake must pass the same contract as the real provider (CLAUDE.md, arnés del proveedor). */
class FakeCalendarSourceContractTest {
    private val account = CalendarAccount("tests@example.com", "LOCAL")

    private fun underTest(): SourceUnderTest {
        val writable = CalendarInfo(
            CalendarId(1),
            account,
            "Mine",
            0xFF0B63CE.toInt(),
            CalendarAccess.OWNER,
            ownerEmail = "me@example.com"
        )
        val readOnly = CalendarInfo(
            CalendarId(2),
            account,
            "Holidays",
            0xFFB3261E.toInt(),
            CalendarAccess.READ,
            ownerEmail = "me@example.com"
        )
        return SourceUnderTest(FakeCalendarSource(listOf(writable, readOnly)), writable, readOnly)
    }

    @TestFactory
    fun `the fake follows the contract`(): List<DynamicTest> =
        CalendarSourceContract.scenarios.map { scenario ->
            DynamicTest.dynamicTest(scenario.name) {
                runTest { scenario.run(underTest()) }
            }
        }
}
