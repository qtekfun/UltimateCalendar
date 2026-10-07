// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R

private val SpinnerSize = 24.dp
private val SpinnerStroke = 2.dp

/**
 * The "Refresh" action of the header: a 48 dp button that turns into a spinner while [refreshing]
 * (and says so to the screen reader). Tapping it while it spins does nothing.
 */
@Composable
fun RefreshButton(refreshing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.refresh_action)
    val busy = stringResource(R.string.refresh_in_progress)
    IconButton(
        onClick = { if (!refreshing) onClick() },
        modifier = modifier.semantics {
            contentDescription = label
            if (refreshing) stateDescription = busy
        }
    ) {
        if (refreshing) {
            CircularProgressIndicator(Modifier.size(SpinnerSize), strokeWidth = SpinnerStroke)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null)
        }
    }
}
