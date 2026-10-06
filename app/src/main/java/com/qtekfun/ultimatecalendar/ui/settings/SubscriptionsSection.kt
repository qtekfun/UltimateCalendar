// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.subscriptions.Subscription
import com.qtekfun.ultimatecalendar.ui.components.CalendarColorDot
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.Instant

/**
 * Settings → Calendar subscriptions (T39): the calendars subscribed to by address, each with its
 * state (last update, events read, last error), and the way to add, change and remove them.
 */
@Composable
fun SubscriptionsSection(viewModel: SubscriptionsViewModel = viewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    NotesToast(viewModel)
    SettingsCard {
        Column(Modifier.padding(Spacing.l)) {
            Hint(stringResource(R.string.subscriptions_hint))
            if (rows.isEmpty()) Hint(stringResource(R.string.subscriptions_none))
        }
        rows.forEach { row ->
            HorizontalDivider()
            SubscriptionItem(row) { editing = row.subscription }
        }
        HorizontalDivider()
        Row(Modifier.padding(horizontal = Spacing.s)) {
            TextButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.s))
                Text(stringResource(R.string.subscriptions_add))
            }
            if (rows.any { it.subscription.enabled }) {
                TextButton(onClick = viewModel::refreshAll) {
                    Text(stringResource(R.string.subscriptions_refresh_all))
                }
            }
        }
    }
    if (adding) AddSubscriptionDialog(viewModel) { adding = false }
    // The row is looked up again, so the dialog follows what a change did to it.
    editing?.let { open ->
        rows.firstOrNull { it.subscription.id == open.id }?.let { current ->
            EditSubscriptionDialog(current.subscription, viewModel) { editing = null }
        } ?: run { editing = null }
    }
}

@Composable
private fun SubscriptionItem(row: SubscriptionRow, onClick: () -> Unit) {
    val subscription = row.subscription
    val status = statusText(row, Instant.now())
    val description = stringResource(
        R.string.subscriptions_row_description,
        subscription.name,
        status
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CalendarColorDot(subscription.color, size = Dimens.dotLarge)
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f)) {
            Text(subscription.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subscription.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (subscription.failing) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (row.refreshing) {
            CircularProgressIndicator(Modifier.size(Dimens.todayIcon), strokeWidth = Spacing.xxs)
        }
    }
}

/** Tells the user how a refresh they asked for went. */
@Composable
private fun NotesToast(viewModel: SubscriptionsViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.notes.collect { note ->
            val text = when (note) {
                is SubscriptionNote.Refreshed -> context.resources.getQuantityString(
                    R.plurals.subscriptions_note_read,
                    note.events,
                    note.events
                )

                SubscriptionNote.Unchanged -> context.getString(
                    R.string.subscriptions_note_unchanged
                )

                is SubscriptionNote.Failed -> context.getString(R.string.subscriptions_note_failed)
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}
