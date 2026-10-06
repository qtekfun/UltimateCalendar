// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus

/** How an event is drawn, from the user's own answer to it. */
enum class EventDisplay {
    /** The user's own event, or an accepted invitation: solid. */
    CONFIRMED,

    /** Answered "maybe": a light tint with a border. */
    TENTATIVE,

    /** An invitation not answered yet: outlined, not filled. */
    PENDING,

    /** Declined: no fill, struck through. */
    DECLINED;

    companion object {
        fun of(status: AttendeeStatus?): EventDisplay = when (status) {
            null, AttendeeStatus.ACCEPTED -> CONFIRMED
            AttendeeStatus.TENTATIVE -> TENTATIVE
            AttendeeStatus.NEEDS_ACTION -> PENDING
            AttendeeStatus.DECLINED -> DECLINED
        }
    }
}

/** The colors of one event chip. [border] is null when the chip has none. */
@Immutable
data class EventChipColors(
    val container: Color,
    val content: Color,
    val border: Color?,
    val strikeThrough: Boolean
)

private const val DARK_TONE = 0.42f
private const val TENTATIVE_TINT_LIGHT = 0.78f
private const val TENTATIVE_TINT_DARK = 0.62f
private const val DARK_SURFACE_LUMINANCE = 0.5

/**
 * The chip colors for an event of [color] (ARGB, the event's or its calendar's) on a screen of
 * [surface] with [onSurface] text. Pure, so contrast is unit tested: text always reaches AA and
 * borders reach 3:1 against the surface. In the dark theme solid fills are toned down toward the
 * surface so they do not glare, as Google Calendar does.
 */
@Suppress("LongParameterList")
fun eventChipColors(
    color: Int,
    display: EventDisplay,
    dark: Boolean,
    surface: Int,
    onSurface: Int,
    onSurfaceVariant: Int
): EventChipColors {
    val border = ColorMath.ensureContrast(color, surface, ColorMath.GRAPHIC_CONTRAST)
    return when (display) {
        EventDisplay.CONFIRMED -> {
            val fill = if (dark) ColorMath.blend(color, surface, DARK_TONE) else color
            EventChipColors(Color(fill), Color(ColorMath.onColor(fill)), null, false)
        }

        EventDisplay.TENTATIVE -> {
            val tint = if (dark) TENTATIVE_TINT_DARK else TENTATIVE_TINT_LIGHT
            val fill = ColorMath.blend(color, surface, tint)
            EventChipColors(Color(fill), Color(ColorMath.onColor(fill)), Color(border), false)
        }

        EventDisplay.PENDING ->
            EventChipColors(Color(surface), Color(onSurface), Color(border), false)

        EventDisplay.DECLINED ->
            EventChipColors(Color.Transparent, Color(onSurfaceVariant), null, true)
    }
}

/** Whether the colors in effect form a dark theme (the app may force it over the system). */
@Composable
fun isDarkTheme(): Boolean =
    ColorMath.luminance(MaterialTheme.colorScheme.surface.toArgb()) < DARK_SURFACE_LUMINANCE

/** [eventChipColors] for the current theme. */
@Composable
fun rememberEventChipColors(color: Int, display: EventDisplay): EventChipColors {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val surface = scheme.surface.toArgb()
    val onSurface = scheme.onSurface.toArgb()
    val onSurfaceVariant = scheme.onSurfaceVariant.toArgb()
    return remember(color, display, dark, surface, onSurface, onSurfaceVariant) {
        eventChipColors(color, display, dark, surface, onSurface, onSurfaceVariant)
    }
}
