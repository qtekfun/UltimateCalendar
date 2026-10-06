// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavAccountState
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules
import com.qtekfun.ultimatecalendar.ui.account.CalDavAccountViewModel

/** Settings → Backup: export to a file and restore from one (RF-11). */
@Composable
fun BackupSection(
    viewModel: BackupViewModel = viewModel(),
    accountModel: CalDavAccountViewModel = viewModel()
) {
    var exporting by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf<CharArray?>(null) }
    var withSession by remember { mutableStateOf(false) }
    // The switch only makes sense with a CalDAV account to include.
    val account by accountModel.state.collectAsStateWithLifecycle()
    val signedIn = account.account is CalDavAccountState.SignedIn
    val create =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val secret = passphrase
            if (uri != null &&
                secret != null
            ) {
                viewModel.export(uri, secret, withSession && signedIn)
            }
            passphrase = null
            withSession = false
        }
    val fileName = stringResource(R.string.backup_file_name)
    SettingsCard {
        Column(Modifier.padding(16.dp)) { Hint(stringResource(R.string.settings_backup_hint)) }
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { exporting = true }) {
                Text(stringResource(R.string.backup_export))
            }
            RestoreBackupButton(viewModel)
        }
    }
    if (exporting) {
        ExportDialog(
            offerSession = signedIn,
            onExport = { secret, includeSession ->
                passphrase = secret
                withSession = includeSession
                exporting = false
                create.launch(fileName)
            },
            onDismiss = { exporting = false }
        )
    }
}

/** "Restore settings", also offered on the first start of a new phone (T12). */
@Composable
fun RestoreBackupButton(viewModel: BackupViewModel = viewModel()) {
    val context = LocalContext.current
    val waiting by viewModel.needsPassphrase.collectAsStateWithLifecycle()
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::startRestore)
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    TextButton(onClick = { open.launch(arrayOf("*/*")) }) {
        Text(stringResource(R.string.backup_restore))
    }
    if (waiting != null) {
        RestoreDialog(onConfirm = viewModel::finishRestore, onDismiss = viewModel::cancelRestore)
    }
}

@Composable
private fun ExportDialog(
    offerSession: Boolean,
    onExport: (CharArray, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    // Off by default: the app password in a file is something to choose on purpose.
    var includeSession by remember { mutableStateOf(false) }
    val valid = SettingsRules.isPassphraseAcceptable(first.toCharArray()) && first == second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralStringResource(
                        R.plurals.backup_passphrase_hint,
                        SettingsRules.MIN_PASSPHRASE_LENGTH,
                        SettingsRules.MIN_PASSPHRASE_LENGTH
                    )
                )
                PassphraseField(first, R.string.backup_passphrase) { first = it }
                PassphraseField(second, R.string.backup_passphrase_repeat) { second = it }
                if (offerSession) {
                    SwitchRow(
                        stringResource(R.string.backup_include_session),
                        stringResource(R.string.backup_include_session_hint),
                        includeSession
                    ) { includeSession = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(first.toCharArray(), offerSession && includeSession) },
                enabled = valid
            ) {
                Text(stringResource(R.string.backup_export))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun RestoreDialog(onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_restore_text))
                PassphraseField(passphrase, R.string.backup_passphrase) { passphrase = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(passphrase.toCharArray())
            }, enabled = passphrase.isNotEmpty()) {
                Text(stringResource(R.string.backup_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun PassphraseField(value: String, label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        )
    )
}
