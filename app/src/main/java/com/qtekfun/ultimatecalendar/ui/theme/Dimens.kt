// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.domain.layout.AdaptiveLayout

/** The spacing scale (4 dp grid) every screen uses instead of loose numbers. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Sizes shared by the calendar views and components. */
object Dimens {
    /** Smallest touch target (accessibility). */
    val minTouch = 48.dp

    /** The circle behind a day number. */
    val dayBadge = 32.dp

    /** Width of the hour labels column in the day and week views. */
    val hourGutter = 56.dp

    /** Height of one hour in the time grid. */
    val hourHeight = 60.dp

    /** An event chip never gets shorter than this, text permitting. */
    val chipMinHeight = 20.dp

    val chipBorder = 1.5.dp
    val dot = 12.dp
    val dotLarge = 16.dp

    /** The calendar icon of the Today button. */
    val todayIcon = 24.dp

    /** Widest a settings page, the event detail or the editor gets: wider is hard to read. */
    val readingMaxWidth = AdaptiveLayout.READING_MAX_DP.dp

    /** Widest a day or a plain list gets on a tablet before it is centered. */
    val wideMaxWidth = AdaptiveLayout.WIDE_MAX_DP.dp

    /** The list pane of the two-pane Agenda. */
    val listPaneWidth = 400.dp

    /** The dialog that holds search or the invitations tray on wide windows. */
    val dialogMaxWidth = 640.dp

    val illustration = 168.dp
    val drawerMaxWidth = 320.dp
}

/** Corner radii. Event chips are tighter than Material's cards, like Google Calendar's. */
object CalendarShapes {
    val eventChip = RoundedCornerShape(4.dp)
    val card = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val snackbar = RoundedCornerShape(8.dp)
}

/** The Material shape scale, tuned: small controls 8 dp, cards 12 dp, big surfaces 28 dp. */
internal val UltimateCalendarShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Elevations: the calendar is mostly flat; only floating things lift. */
object Elevations {
    val none = 0.dp
    val chip = 0.dp
    val fab = 3.dp
    val sheet = 1.dp
    val snackbar = 6.dp
}
