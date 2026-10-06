// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.qtekfun.ultimatecalendar.ui.theme.ThemeOptions
import com.qtekfun.ultimatecalendar.ui.theme.UltimateCalendarTheme
import java.time.LocalDate

/** Light, dark and 200% font, the three states every component preview must show. */
@Preview(name = "Light", showBackground = true, widthDp = 360)
@Preview(
    name = "Dark",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "Large font", showBackground = true, widthDp = 360, fontScale = 2f)
annotation class ComponentPreviews

/** Theme and background for previews: fixed colors (no wallpaper) so previews are stable. */
@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) {
    UltimateCalendarTheme(ThemeOptions(dynamicColor = false)) {
        Surface(content = content)
    }
}

/** The "today" every preview shows, so previews do not change with the calendar. */
internal val PreviewToday: LocalDate = LocalDate.parse("2026-10-06")
