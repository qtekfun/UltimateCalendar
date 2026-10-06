// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.account

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.auth.LoginError
import com.qtekfun.ultimatecalendar.domain.auth.LoginState
import com.qtekfun.ultimatecalendar.domain.auth.LoginUiState
import com.qtekfun.ultimatecalendar.ui.theme.Dimens

/**
 * The login of the CalDAV connection (RF-12): the server address, then Nextcloud Login Flow v2
 * in the browser. Opens the browser by itself when the wait starts, and offers a button to open
 * it again. Thin: the rules are in [LoginUiState].
 */
@Composable
fun CalDavLoginScreen(viewModel: CalDavLoginViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val loginUrl = state.loginUrl
    LaunchedEffect(loginUrl) { loginUrl?.let { uriHandler.openUri(it) } }
    CalDavLoginContent(
        state = state,
        onServerChange = viewModel::onServerChange,
        onConnect = viewModel::connect,
        onOpenBrowser = { loginUrl?.let { uriHandler.openUri(it) } },
        onCancel = viewModel::cancel,
        modifier = modifier
    )
}

@Composable
internal fun CalDavLoginContent(
    state: LoginUiState,
    onServerChange: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.caldav_login_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            stringResource(R.string.caldav_login_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AddressField(state, onServerChange, onConnect)
        LoginProgress(state, onConnect, onOpenBrowser, onCancel)
    }
}

@Composable
private fun AddressField(state: LoginUiState, onChange: (String) -> Unit, onConnect: () -> Unit) {
    val error = state.error
    OutlinedTextField(
        value = state.server,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.caldav_login_server_label)) },
        placeholder = { Text(stringResource(R.string.caldav_login_server_hint)) },
        supportingText = {
            when {
                error != null -> Text(stringResource(errorText(error)))

                state.target != null ->
                    Text(stringResource(R.string.caldav_login_target, state.target.orEmpty()))
            }
        },
        isError = error != null,
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(onGo = { onConnect() }),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LoginProgress(
    state: LoginUiState,
    onConnect: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCancel: () -> Unit
) {
    when (state.step) {
        null, is LoginState.Failed -> Button(
            onClick = onConnect,
            enabled = state.canSubmit,
            modifier = Modifier.heightIn(min = Dimens.minTouch)
        ) { Text(stringResource(R.string.caldav_login_button)) }

        LoginState.CheckingServer -> Busy(R.string.caldav_login_checking)

        is LoginState.WaitingForBrowser -> {
            Busy(R.string.caldav_login_waiting)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenBrowser,
                    modifier = Modifier.heightIn(min = Dimens.minTouch)
                ) { Text(stringResource(R.string.caldav_login_open_browser)) }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = Dimens.minTouch)
                ) { Text(stringResource(R.string.caldav_login_cancel)) }
            }
        }

        is LoginState.LoggedIn -> Busy(R.string.caldav_login_signing_in)
    }
}

@Composable
private fun Busy(@StringRes text: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@StringRes
internal fun errorText(error: LoginError): Int = when (error) {
    LoginError.INVALID_URL -> R.string.caldav_login_error_invalid_url
    LoginError.INSECURE_URL -> R.string.caldav_login_error_insecure
    LoginError.NOT_NEXTCLOUD -> R.string.caldav_login_error_not_nextcloud
    LoginError.UNREACHABLE -> R.string.caldav_login_error_unreachable
    LoginError.TLS_ERROR -> R.string.caldav_login_error_tls
    LoginError.EXPIRED -> R.string.caldav_login_error_expired
    LoginError.UNKNOWN -> R.string.caldav_login_error_unknown
}
