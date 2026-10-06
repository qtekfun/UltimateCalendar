// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.ui.firstrun.PhoneSettings
import com.qtekfun.ultimatecalendar.ui.firstrun.ReminderPermissions
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

private const val MIN_TOUCH_DP = 48

/**
 * A row under the top bar for when the calendar permission is missing: the calendars of the
 * phone's own accounts are not readable, while CalDAV and subscriptions keep working. [onResult]
 * runs after the system dialog so the calendars are read again.
 */
@Composable
internal fun CalendarPermissionBanner(onResult: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var blocked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        blocked = !granted.values.all { it }
        onResult()
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l)
                .heightIn(min = MIN_TOUCH_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(
                    if (blocked) {
                        R.string.shell_permission_blocked
                    } else {
                        R.string.shell_permission_needed
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    // Once the system stops showing its dialog, only the app's settings can grant it.
                    if (blocked) {
                        PhoneSettings.openAppSettings(context)
                    } else {
                        request.launch(ReminderPermissions.CALENDAR)
                    }
                }
            ) {
                Text(
                    stringResource(
                        if (blocked) R.string.shell_permission_settings else R.string.wizard_allow
                    )
                )
            }
        }
    }
}
