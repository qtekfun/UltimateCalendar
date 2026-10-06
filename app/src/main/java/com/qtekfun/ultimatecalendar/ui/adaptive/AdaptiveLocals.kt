// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import com.qtekfun.ultimatecalendar.domain.layout.AdaptiveLayout

/**
 * Forces a layout instead of reading the window: the debug demo uses it to show the tablet
 * layouts on a phone. Null (the default) means "the layout of the window".
 */
val LocalAdaptiveLayout = compositionLocalOf<AdaptiveLayout?> { null }

/**
 * The layout of the current window. It follows the size of the window, so rotation,
 * multi-window resizing and a fold all produce a new value and recompose what depends on it.
 */
@Composable
fun currentAdaptiveLayout(): AdaptiveLayout {
    val forced = LocalAdaptiveLayout.current
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current.density
    return forced ?: AdaptiveLayout.of((widthPx / density).toInt())
}

/**
 * Draws [content] with the hour grid's text and hours scaled for the window (bigger on tablets).
 * The grid sizes itself from the font scale, so this is the only thing it needs.
 */
@Composable
fun WithAdaptiveGridScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val factor = currentAdaptiveLayout().gridTextFactor(density.fontScale)
    if (factor == 1f) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale * factor),
            content = content
        )
    }
}
