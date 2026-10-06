// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import com.qtekfun.ultimatecalendar.domain.widget.DynamicTones
import com.qtekfun.ultimatecalendar.domain.widget.WidgetPalette

/** The colors of the widgets from the user's theme (RF-10): light, dark, AMOLED, Material You. */
internal object WidgetTheme {
    fun palette(context: Context, settings: AppSettings): WidgetPalette {
        val systemDark = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = when (settings.theme) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDark
        }
        val tones = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            tonesOf(context)
        } else {
            null
        }
        return WidgetPalette.of(dark, settings.amoled, tones)
    }

    /** The system's tonal colors; they exist from Android 12, where the caller checked. */
    private fun tonesOf(context: Context): DynamicTones = DynamicTones(
        neutral10 = context.getColor(android.R.color.system_neutral1_10),
        neutral100 = context.getColor(android.R.color.system_neutral1_100),
        neutral900 = context.getColor(android.R.color.system_neutral1_900),
        neutralVariant200 = context.getColor(android.R.color.system_neutral2_200),
        neutralVariant700 = context.getColor(android.R.color.system_neutral2_700),
        accent0 = context.getColor(android.R.color.system_accent1_0),
        accent200 = context.getColor(android.R.color.system_accent1_200),
        accent600 = context.getColor(android.R.color.system_accent1_600),
        accent800 = context.getColor(android.R.color.system_accent1_800)
    )
}

/** Tints an image of a widget (they are drawn white): a RemoteViews cannot set a tint list before Android 12. */
internal fun RemoteViews.tint(viewId: Int, color: Int) = setInt(viewId, "setColorFilter", color)

/** Gives the widget its card color. */
internal fun RemoteViews.paintBackground(palette: WidgetPalette) =
    tint(R.id.widget_background, palette.background)
