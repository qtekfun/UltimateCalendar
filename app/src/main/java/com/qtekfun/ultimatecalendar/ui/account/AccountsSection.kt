// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavAccountState
import com.qtekfun.ultimatecalendar.ui.settings.Hint
import com.qtekfun.ultimatecalendar.ui.settings.RowMinHeight
import com.qtekfun.ultimatecalendar.ui.settings.SettingsCard

/**
 * Settings › Accounts (RF-12): connect a CalDAV server, or, once connected, open its account.
 * [onOpen] goes to [CalDavAccountRoute] either way.
 */
@Composable
fun AccountsSection(onOpen: () -> Unit, viewModel: CalDavAccountViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountsRow(state.account as? CalDavAccountState.SignedIn, onOpen)
}

@Composable
internal fun AccountsRow(signedIn: CalDavAccountState.SignedIn?, onOpen: () -> Unit) {
    SettingsCard {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (signedIn == null) {
                    Text(stringResource(R.string.account_connect_title))
                    Hint(stringResource(R.string.account_connect_hint))
                } else {
                    Text(
                        stringResource(R.string.account_signed_in_title),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Hint(
                        stringResource(
                            R.string.account_signed_in_hint,
                            signedIn.account.loginName,
                            signedIn.account.serverUrl
                        )
                    )
                }
            }
        }
    }
}
