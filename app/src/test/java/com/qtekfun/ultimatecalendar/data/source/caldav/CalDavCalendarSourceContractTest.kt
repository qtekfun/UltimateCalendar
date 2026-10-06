// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import com.qtekfun.ultimatecalendar.data.source.CalendarSourceContract
import com.qtekfun.ultimatecalendar.data.source.CompositeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.SourceUnderTest
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * The shared contract against the CalDAV source over a real in-memory Room and a fake CalDAV
 * server, and against the composite with the CalDAV calendars on one side or the other. Real
 * threads (`runBlocking`): the contract waits for Room's own invalidation.
 */
class CalDavCalendarSourceContractTest {
    @StartStop
    val server = MockWebServer()

    private val androidAccount = CalendarAccount("tests@example.com", "LOCAL")

    private fun provider(id: Long, name: String, access: CalendarAccess) = CalendarInfo(
        CalendarId(id),
        androidAccount,
        name,
        0xFF0B63CE.toInt(),
        access,
        ownerEmail = "me@example.com"
    )

    private fun run(
        scenario: com.qtekfun.ultimatecalendar.data.source.Scenario,
        build: suspend (CalDavRig) -> SourceUnderTest
    ) = runBlocking {
        val rig = CalDavRig(server).setUp()
        try {
            scenario.run(build(rig))
        } finally {
            rig.close()
        }
    }

    @TestFactory
    fun `the CalDAV source follows the contract`(): List<DynamicTest> =
        CalendarSourceContract.scenarios.map { scenario ->
            DynamicTest.dynamicTest(scenario.name) { run(scenario) { it.underTest() } }
        }

    /** CalDAV calendars written through the composite, next to provider ones with colliding ids. */
    @TestFactory
    fun `the composite follows the contract with the CalDAV calendars`(): List<DynamicTest> =
        CalendarSourceContract.scenarios.map { scenario ->
            DynamicTest.dynamicTest(scenario.name) {
                run(scenario) { rig ->
                    val calendars = FakeCalendarSource(
                        listOf(
                            provider(1, "Phone", CalendarAccess.OWNER),
                            provider(2, "Phone holidays", CalendarAccess.READ)
                        )
                    )
                    val under = rig.underTest()
                    SourceUnderTest(
                        CompositeCalendarSource(calendars, rig.source),
                        under.writable,
                        under.readOnly
                    )
                }
            }
        }

    /** The provider's calendars through the composite, with a signed-in CalDAV account beside. */
    @TestFactory
    fun `the composite follows the contract with the provider calendars`(): List<DynamicTest> =
        CalendarSourceContract.scenarios.map { scenario ->
            DynamicTest.dynamicTest(scenario.name) {
                run(scenario) { rig ->
                    val writable = provider(1, "Phone", CalendarAccess.OWNER)
                    val readOnly = provider(2, "Phone holidays", CalendarAccess.READ)
                    SourceUnderTest(
                        CompositeCalendarSource(
                            FakeCalendarSource(listOf(writable, readOnly)),
                            rig.source
                        ),
                        writable,
                        readOnly
                    )
                }
            }
        }
}
