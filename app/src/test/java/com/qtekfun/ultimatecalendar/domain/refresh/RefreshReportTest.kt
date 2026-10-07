// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.refresh

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RefreshReportTest {
    @Test
    fun `a report with no issue is up to date`() {
        assertTrue(RefreshReport().upToDate)
        assertEquals(emptyList<RefreshIssue>(), RefreshReport().ordered)
    }

    @Test
    fun `a report with an issue is not, and lists its issues in a fixed order`() {
        val report = RefreshReport(
            linkedSetOf(RefreshIssue.SERVER_ERROR, RefreshIssue.OFFLINE, RefreshIssue.READ_FAILED)
        )

        assertFalse(report.upToDate)
        assertEquals(
            listOf(RefreshIssue.OFFLINE, RefreshIssue.SERVER_ERROR, RefreshIssue.READ_FAILED),
            report.ordered
        )
    }
}
