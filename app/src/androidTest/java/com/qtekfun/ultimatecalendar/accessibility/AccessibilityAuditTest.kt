// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.accessibility

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.ui.agenda.AgendaListPreview
import com.qtekfun.ultimatecalendar.ui.components.CalendarRowsPreview
import com.qtekfun.ultimatecalendar.ui.components.CalendarTopBarPreview
import com.qtekfun.ultimatecalendar.ui.components.CreateFabPreview
import com.qtekfun.ultimatecalendar.ui.components.DayBadgePreview
import com.qtekfun.ultimatecalendar.ui.components.EmptyStatePreview
import com.qtekfun.ultimatecalendar.ui.components.EventChipPreview
import com.qtekfun.ultimatecalendar.ui.components.TodayButtonPreview
import com.qtekfun.ultimatecalendar.ui.detail.InvitationDetailPreview
import com.qtekfun.ultimatecalendar.ui.editor.EditorTimedPreview
import com.qtekfun.ultimatecalendar.ui.invitations.InvitationsContentPreview
import com.qtekfun.ultimatecalendar.ui.month.MonthPreview
import com.qtekfun.ultimatecalendar.ui.search.SearchResultsPreview
import com.qtekfun.ultimatecalendar.ui.timegrid.WeekPreview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [SemanticsAudit] over the main screens drawn with their invented preview data, at normal and
 * 200 % font. Month chips, "+N" cells and short grid blocks are smaller than 48 dp on purpose
 * (SPEC §6, "Accesibilidad (T26)"): Compose widens their touch area and the same events are
 * reachable at full size from the day or the Agenda, so those two screens skip the size rule only.
 */
class AccessibilityAuditTest {
    @get:Rule
    val compose = createComposeRule()

    private fun audit(
        allowSmall: Boolean = false,
        fontScale: Float = 1f,
        screen: @Composable () -> Unit
    ): List<String> {
        var density = Density(1f)
        compose.setContent {
            val current = LocalDensity.current
            density = Density(current.density, fontScale)
            CompositionLocalProvider(LocalDensity provides density) { screen() }
        }
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode()
        return SemanticsAudit.violations(root, density, allowSmall)
    }

    private fun clean(
        allowSmall: Boolean = false,
        fontScale: Float = 1f,
        screen: @Composable () -> Unit
    ) {
        val found = audit(allowSmall, fontScale, screen)
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun theAuditCatchesASilentButton() {
        val silent = audit { Box(Modifier.size(MIN_SIZE).clickable { }) }
        assertTrue(silent.toString(), silent.any { it.startsWith("silent actionable") })
    }

    @Test fun agenda() = clean { AgendaListPreview() }

    @Test fun agendaAtLargeFont() = clean(fontScale = LARGE) { AgendaListPreview() }

    @Test fun week() = clean(allowSmall = true) { WeekPreview() }

    @Test fun month() = clean(allowSmall = true) { MonthPreview() }

    @Test fun monthAtLargeFont() = clean(allowSmall = true, fontScale = LARGE) { MonthPreview() }

    @Test fun topBar() = clean { CalendarTopBarPreview() }

    @Test fun topBarAtLargeFont() = clean(fontScale = LARGE) { CalendarTopBarPreview() }

    @Test fun createButton() = clean { CreateFabPreview() }

    @Test fun todayButton() = clean { TodayButtonPreview() }

    @Test fun dayBadges() = clean(fontScale = LARGE) { DayBadgePreview() }

    @Test fun eventChips() = clean(allowSmall = true) { EventChipPreview() }

    @Test fun calendarRows() = clean(fontScale = LARGE) { CalendarRowsPreview() }

    @Test fun emptyState() = clean(fontScale = LARGE) { EmptyStatePreview() }

    @Test fun invitationsInbox() = clean { InvitationsContentPreview() }

    @Test fun invitationsInboxAtLargeFont() = clean(fontScale = LARGE) {
        InvitationsContentPreview()
    }

    @Test fun search() = clean { SearchResultsPreview() }

    @Test fun eventDetail() = clean { InvitationDetailPreview() }

    @Test fun eventEditor() = clean { EditorTimedPreview() }

    @Test fun eventEditorAtLargeFont() = clean(fontScale = LARGE) { EditorTimedPreview() }

    private companion object {
        const val LARGE = 2f
        val MIN_SIZE = 48.dp
    }
}
