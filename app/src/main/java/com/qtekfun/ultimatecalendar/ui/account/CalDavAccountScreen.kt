// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.account

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavAccountState
import com.qtekfun.ultimatecalendar.domain.caldav.CalDavCalendarItem
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.sync.engine.SyncStatus
import com.qtekfun.ultimatecalendar.ui.settings.Hint
import com.qtekfun.ultimatecalendar.ui.settings.RowMinHeight
import com.qtekfun.ultimatecalendar.ui.settings.SectionTitle
import com.qtekfun.ultimatecalendar.ui.settings.SettingsCard
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import java.time.Instant

private val DOT_SIZE = 14.dp

/**
 * The signed-in CalDAV account (RF-12): who and where (never the app password), how the sync is
 * going with "Sync now", the server's calendars with a switch each, and "Sign out". Thin: the
 * state is built by the repository.
 */
@Composable
internal fun CalDavAccountContent(
    state: AccountUiState,
    signedIn: CalDavAccountState.SignedIn,
    onSyncNow: () -> Unit,
    onSetEnabled: (CalendarId, Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsCard {
            InfoRow(stringResource(R.string.caldav_account_server), signedIn.account.serverUrl)
            HorizontalDivider()
            InfoRow(stringResource(R.string.caldav_account_user), signedIn.account.loginName)
        }
        SectionTitle(stringResource(R.string.caldav_account_sync_section))
        SyncCard(state.status, signedIn, onSyncNow)
        SectionTitle(stringResource(R.string.caldav_calendars_section))
        CalendarsCard(state.calendars, onSetEnabled)
        OutlinedButton(
            onClick = { confirming = true },
            enabled = !state.signingOut,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch)
        ) {
            Text(
                stringResource(
                    if (state.signingOut) R.string.caldav_signing_out else R.string.caldav_sign_out
                )
            )
        }
    }
    if (confirming) {
        SignOutDialog(
            onConfirm = {
                confirming = false
                onSignOut()
            },
            onDismiss = { confirming = false }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(16.dp, 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Hint(label)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SyncCard(
    status: SyncStatus,
    signedIn: CalDavAccountState.SignedIn,
    onSyncNow: () -> Unit
) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                statusText(status),
                style = MaterialTheme.typography.bodyLarge,
                color = if (status.phase.isProblem()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            status.lastOk?.let { Hint(stringResource(R.string.caldav_sync_last, relative(it))) }
            if (signedIn.pending > 0) {
                Hint(
                    pluralStringResource(
                        R.plurals.caldav_pending_changes,
                        signedIn.pending,
                        signedIn.pending
                    )
                )
            }
            if (signedIn.failed > 0) {
                Hint(
                    pluralStringResource(
                        R.plurals.caldav_failed_changes,
                        signedIn.failed,
                        signedIn.failed
                    )
                )
            }
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Button(
                onClick = onSyncNow,
                enabled = status.canRetry,
                modifier = Modifier.heightIn(min = Dimens.minTouch)
            ) {
                Text(
                    stringResource(
                        if (status.phase.isProblem()) {
                            R.string.caldav_sync_retry
                        } else {
                            R.string.caldav_sync_now
                        }
                    )
                )
            }
        }
    }
}

private fun SyncStatus.Phase.isProblem() = this == SyncStatus.Phase.OFFLINE ||
    this == SyncStatus.Phase.REFUSED ||
    this == SyncStatus.Phase.FAILED

@Composable
private fun statusText(status: SyncStatus): String = stringResource(
    when (status.phase) {
        SyncStatus.Phase.SYNCING -> R.string.caldav_sync_syncing
        SyncStatus.Phase.NEVER -> R.string.caldav_sync_never
        SyncStatus.Phase.OK -> R.string.caldav_sync_ok
        SyncStatus.Phase.OFFLINE -> R.string.caldav_sync_offline
        SyncStatus.Phase.REFUSED -> R.string.caldav_sync_refused
        SyncStatus.Phase.FAILED -> R.string.caldav_sync_failed
    }
)

private fun relative(at: Instant): String = DateUtils.getRelativeTimeSpanString(
    at.toEpochMilli(),
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS
).toString()

@Composable
private fun CalendarsCard(
    calendars: List<CalDavCalendarItem>,
    onSetEnabled: (CalendarId, Boolean) -> Unit
) {
    SettingsCard {
        Column(Modifier.padding(16.dp)) { Hint(stringResource(R.string.caldav_calendars_hint)) }
        if (calendars.isEmpty()) {
            Text(
                stringResource(R.string.caldav_calendars_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        calendars.forEach { calendar ->
            HorizontalDivider()
            CalendarSwitchRow(calendar) { onSetEnabled(calendar.id, it) }
        }
    }
}

@Composable
private fun CalendarSwitchRow(calendar: CalDavCalendarItem, onChange: (Boolean) -> Unit) {
    val description = stringResource(R.string.caldav_calendar_switch, calendar.name)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .toggleable(value = calendar.enabled, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(DOT_SIZE)
                .background(Color(calendar.color), CircleShape)
                .clearAndSetSemantics { }
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(calendar.name)
            if (!calendar.writable) Hint(stringResource(R.string.caldav_calendar_read_only))
        }
        Switch(checked = calendar.enabled, onCheckedChange = null)
    }
}

@Composable
private fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.caldav_sign_out_title)) },
        text = { Text(stringResource(R.string.caldav_sign_out_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.caldav_sign_out_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
