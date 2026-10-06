// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.firstrun

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.firstrun.SetupItem
import com.qtekfun.ultimatecalendar.domain.firstrun.SetupStatus
import com.qtekfun.ultimatecalendar.domain.firstrun.SetupStep

/**
 * The first-run wizard and the reliability steps (RF-01, RF-08): each step says why it is asked
 * and whether it is done. Shown the first time the app opens, and from anywhere through
 * [LocalOpenWizard]. [onDone] is called when the user finishes or leaves it.
 */
@Composable
fun ReliabilityWizardScreen(onDone: () -> Unit, viewModel: FirstRunViewModel = viewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    // Permissions change in other screens and in the system's settings: read them again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    Scaffold(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.wizard_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.wizard_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            status?.let { current ->
                viewModel.plan(current).forEach { item -> StepCard(item, current, viewModel) }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_done))
            }
        }
    }
}

@Composable
private fun StepCard(item: SetupItem, status: SetupStatus, viewModel: FirstRunViewModel) {
    val context = LocalContext.current
    val done = item.state == SetupItem.State.DONE
    when (item.step) {
        SetupStep.CALENDAR_PERMISSION -> CalendarPermissionCard(done, viewModel::refresh)

        SetupStep.ADD_ACCOUNT -> WizardCard(
            R.string.wizard_account_title,
            R.string.wizard_account_why,
            action = Action(R.string.wizard_open_accounts) {
                PhoneSettings.openAccounts(context)
            }
        )

        SetupStep.NOTIFICATIONS -> NotificationsCard(done, viewModel::refresh)

        SetupStep.EXACT_ALARMS -> WizardCard(
            R.string.wizard_exact_title,
            R.string.wizard_exact_why,
            done,
            Action(R.string.wizard_allow) { ReminderPermissions.askExactAlarms(context) }
        )

        SetupStep.BATTERY -> WizardCard(
            R.string.wizard_battery_title,
            R.string.wizard_battery_why,
            done,
            Action(R.string.wizard_allow) { ReminderPermissions.askBatteryExemption(context) }
        )

        SetupStep.AUTOSTART -> MakerCard(status.maker)

        SetupStep.OTHER_CALENDAR_APPS -> OtherAppsCard(status.otherCalendarApps, viewModel)

        SetupStep.TEST_REMINDER -> TestReminderCard(viewModel)
    }
}

@Composable
private fun CalendarPermissionCard(done: Boolean, onResult: () -> Unit) {
    val context = LocalContext.current
    var blocked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        blocked = !granted.values.all { it }
        onResult()
    }
    WizardCard(
        R.string.wizard_calendar_title,
        R.string.wizard_calendar_why,
        done,
        Action(R.string.wizard_allow) {
            // Once the system stops showing its dialog, only the app's settings can grant it.
            if (blocked) {
                PhoneSettings.openAppSettings(context)
            } else {
                request.launch(ReminderPermissions.CALENDAR)
            }
        },
        hint = if (blocked && !done) R.string.wizard_calendar_blocked else null
    )
}

@Composable
private fun NotificationsCard(done: Boolean, onResult: () -> Unit) {
    val context = LocalContext.current
    var blocked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        blocked = !granted
        onResult()
    }
    val needsDialog = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !ReminderPermissions.notificationPermissionGranted(context) && !blocked
    WizardCard(
        R.string.wizard_notifications_title,
        R.string.wizard_notifications_why,
        done,
        Action(
            if (needsDialog) R.string.wizard_allow else R.string.wizard_open_notification_settings
        ) {
            if (needsDialog) {
                request.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                PhoneSettings.openNotificationSettings(context)
            }
        },
        hint = if (blocked && !done) R.string.wizard_notifications_blocked else null
    )
}

/** What a step's button says and does. */
internal class Action(val label: Int, val onClick: () -> Unit)

/** One card per thing to allow; [done] is null for advice the app cannot check. */
@Composable
internal fun WizardCard(
    title: Int,
    why: Int,
    done: Boolean? = null,
    action: Action? = null,
    hint: Int? = null
) {
    StepSurface {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            stringResource(why),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        hint?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (done == true) {
            Text(
                stringResource(R.string.wizard_allowed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (action != null) {
            Button(onClick = action.onClick) { Text(stringResource(action.label)) }
        }
    }
}
