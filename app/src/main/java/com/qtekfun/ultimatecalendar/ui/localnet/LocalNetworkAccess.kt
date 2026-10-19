// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.localnet

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatecalendar.R

/**
 * Explains why the local network permission is needed, asks for it, and, once it was refused,
 * opens the app's Settings page where it can still be allowed. [host] is the server the app talks
 * to now, or null when there is none yet. It looks again each time the app comes back to the
 * foreground, so allowing it in Settings takes effect at once.
 */
@Composable
fun LocalNetworkAccess(viewModel: LocalNetworkViewModel, host: String?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(host) {
        viewModel.check(host)
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        viewModel::onPermissionResult
    )
    val blockedHost = state.blockedHost
    if (!state.showDialog || blockedHost == null) return
    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text(stringResource(R.string.local_network_title)) },
        text = {
            val message = if (state.denied) {
                R.string.local_network_denied
            } else {
                R.string.local_network_rationale
            }
            Text(stringResource(message, blockedHost))
        },
        confirmButton = {
            if (state.denied) {
                TextButton(
                    onClick = { context.startActivity(appSettingsIntent(context.packageName)) }
                ) { Text(stringResource(R.string.local_network_open_settings)) }
            } else {
                TextButton(onClick = { launcher.launch(LocalNetwork.PERMISSION) }) {
                    Text(stringResource(R.string.local_network_allow))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismiss) {
                Text(stringResource(R.string.local_network_not_now))
            }
        }
    )
}

/** The system's page of this app, where its permissions can be changed. */
private fun appSettingsIntent(packageName: String): Intent {
    val uri = Uri.fromParts("package", packageName, null)
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
