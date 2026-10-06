// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R

/**
 * Decides whether the Create button is expanded (icon and label) or collapsed (icon), from the
 * scroll of the content under it: scrolling down collapses, scrolling up expands. Small moves
 * under [threshold] pixels are ignored so jitter does not make it flicker.
 */
class FabScrollTracker(private val threshold: Float = DEFAULT_THRESHOLD) {
    var expanded: Boolean = true
        private set

    private var travelled = 0f

    /** [delta] is the content's scroll in pixels: positive when scrolling toward the end. */
    fun onScroll(delta: Float) {
        if (delta == 0f) return
        if (travelled != 0f && (travelled > 0f) != (delta > 0f)) travelled = 0f
        travelled += delta
        if (travelled > threshold) {
            expanded = false
        } else if (travelled < -threshold) {
            expanded = true
        }
    }

    /** Back at the top of the content: always expanded. */
    fun reset() {
        expanded = true
        travelled = 0f
    }

    private companion object {
        const val DEFAULT_THRESHOLD = 24f
    }
}

/** The expanded flag and the connection to attach to the content with `Modifier.nestedScroll`. */
class FabScrollState internal constructor() {
    private val tracker = FabScrollTracker()
    var expanded by mutableStateOf(true)
        private set

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Content scrolling toward its end moves the finger up: available.y is negative.
            tracker.onScroll(-available.y)
            expanded = tracker.expanded
            return Offset.Zero
        }
    }
}

@Composable
fun rememberFabScrollState(): FabScrollState = remember { FabScrollState() }

/**
 * The "Create" button of Google Calendar: icon and label, collapsing to the icon while the user
 * scrolls down. [expanded] false still keeps a 56 dp touch target and the same description.
 */
@Composable
fun CreateFab(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        modifier = modifier,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.shell_create)) }
    )
}

@ComponentPreviews
@Composable
internal fun CreateFabPreview() {
    PreviewSurface {
        Box { CreateFab(expanded = true, onClick = {}) }
    }
}
