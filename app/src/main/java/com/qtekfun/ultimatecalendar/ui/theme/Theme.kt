// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode

internal val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = BlueOnContainerLight,
    secondary = Orange40,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = SlateOnContainerLight,
    tertiary = Red40,
    background = SurfaceLight,
    surface = SurfaceLight
)

internal val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = BlueOnPrimaryDark,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = BlueOnContainerDark,
    secondary = Orange80,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = SlateOnContainerDark,
    tertiary = Red80,
    background = SurfaceDark,
    surface = SurfaceDark
)

/**
 * The color scheme for [options]. [dynamicLight] and [dynamicDark] are the wallpaper
 * colors (Android 12+), or null where they do not exist.
 */
fun colorSchemeFor(
    options: ThemeOptions,
    systemDark: Boolean,
    dynamicLight: ColorScheme?,
    dynamicDark: ColorScheme?
): ColorScheme {
    val dark = when (options.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamic = if (dark) dynamicDark else dynamicLight
    val scheme =
        (if (options.dynamicColor) dynamic else null) ?: if (dark) DarkColors else LightColors
    return if (dark && options.amoled) scheme.toAmoled() else scheme
}

/** Pure black behind everything, for OLED screens; containers stay just visible. */
internal fun ColorScheme.toAmoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = AmoledLow,
    surfaceContainer = AmoledContainer,
    surfaceContainerHigh = AmoledHigh,
    surfaceContainerHighest = AmoledHighest,
    surfaceBright = AmoledHighest
)

@Composable
fun UltimateCalendarTheme(options: ThemeOptions = ThemeOptions(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = colorSchemeFor(
        options = options,
        systemDark = isSystemInDarkTheme(),
        dynamicLight = if (dynamic) dynamicLightColorScheme(context) else null,
        dynamicDark = if (dynamic) dynamicDarkColorScheme(context) else null
    )
    CompositionLocalProvider(LocalCalendarTypography provides CalendarTypography()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UltimateCalendarTypography,
            shapes = UltimateCalendarShapes,
            content = content
        )
    }
}
