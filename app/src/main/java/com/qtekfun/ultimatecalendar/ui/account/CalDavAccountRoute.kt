// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavAccountState
import com.qtekfun.ultimatecalendar.ui.adaptive.ReadingPane

/**
 * Settings › Accounts › CalDAV (RF-12): the login while nobody is signed in, the account once
 * somebody is. The account state decides, so signing in or out swaps the content by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalDavAccountRoute(
    onBack: () -> Unit,
    accountModel: CalDavAccountViewModel = viewModel(),
    loginModel: CalDavLoginViewModel = viewModel()
) {
    val state by accountModel.state.collectAsStateWithLifecycle()
    val signedIn = state.account as? CalDavAccountState.SignedIn
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (signedIn == null) {
                                R.string.caldav_login_title
                            } else {
                                R.string.caldav_account_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        ReadingPane(Modifier.padding(padding)) {
            when {
                signedIn != null -> CalDavAccountContent(
                    state = state,
                    signedIn = signedIn,
                    onSyncNow = accountModel::syncNow,
                    onSetEnabled = accountModel::setEnabled,
                    onSignOut = accountModel::signOut
                )

                state.signedOut -> CalDavLoginScreen(loginModel, Modifier)

                // Not read yet: nothing to show for a moment.
            }
        }
    }
}
