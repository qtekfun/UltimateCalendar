// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.subscriptions.AddResult
import com.qtekfun.ultimatecalendar.domain.editor.EventColorChoice
import com.qtekfun.ultimatecalendar.domain.subscriptions.RefreshInterval
import com.qtekfun.ultimatecalendar.domain.subscriptions.Subscription
import com.qtekfun.ultimatecalendar.ui.editor.ColorSwatch
import com.qtekfun.ultimatecalendar.ui.editor.colorName
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import kotlinx.coroutines.launch

/** The address, name, color and refresh of a new subscription; stays open until it is accepted. */
@Composable
internal fun AddSubscriptionDialog(viewModel: SubscriptionsViewModel, onDismiss: () -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(EventColorChoice.PEACOCK.argb) }
    var interval by rememberSaveable { mutableStateOf(RefreshInterval.DEFAULT) }
    var problem by remember { mutableStateOf<AddResult?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_add)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        problem = null
                    },
                    label = { Text(stringResource(R.string.subscriptions_url_label)) },
                    isError = problem != null,
                    supportingText = problem?.let { { Text(problemText(it)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                SubscriptionFields(
                    name,
                    color,
                    onName = { name = it },
                    onColor = { color = it }
                )
                ChoiceRow(
                    stringResource(R.string.subscriptions_interval),
                    intervalName(interval),
                    RefreshInterval.entries,
                    { intervalName(it) }
                ) { interval = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val result = viewModel.add(address, name, color, interval)
                        if (result is AddResult.Added) onDismiss() else problem = result
                    }
                },
                enabled = address.isNotBlank()
            ) { Text(stringResource(R.string.subscriptions_add_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Everything about one subscription: rename, recolor, switch on or off, refresh, remove. */
@Composable
internal fun EditSubscriptionDialog(
    subscription: Subscription,
    viewModel: SubscriptionsViewModel,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(subscription.id) { mutableStateOf(subscription.name) }
    var color by rememberSaveable(subscription.id) { mutableStateOf(subscription.color) }
    var confirmRemove by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(subscription.host) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                SubscriptionFields(name, color, onName = { name = it }, onColor = { color = it })
                SwitchRow(
                    stringResource(R.string.subscriptions_enabled),
                    null,
                    subscription.enabled
                ) { viewModel.setEnabled(subscription.id, it) }
                ChoiceRow(
                    stringResource(R.string.subscriptions_interval),
                    intervalName(subscription.interval),
                    RefreshInterval.entries,
                    { intervalName(it) }
                ) { viewModel.setInterval(subscription.id, it) }
                Row {
                    TextButton(onClick = { viewModel.refresh(subscription.id) }) {
                        Text(stringResource(R.string.subscriptions_refresh_now))
                    }
                    TextButton(onClick = { confirmRemove = true }) {
                        Text(stringResource(R.string.subscriptions_remove))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.edit(subscription.id, name, color)
                onDismiss()
            }) { Text(stringResource(R.string.subscriptions_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
    if (confirmRemove) {
        RemoveSubscriptionDialog(
            subscription.name,
            onRemove = {
                viewModel.remove(subscription.id)
                onDismiss()
            },
            onDismiss = { confirmRemove = false }
        )
    }
}

@Composable
private fun RemoveSubscriptionDialog(name: String, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_remove_title)) },
        text = { Text(stringResource(R.string.subscriptions_remove_text, name)) },
        confirmButton = {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.subscriptions_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionFields(
    name: String,
    color: Int,
    onName: (String) -> Unit,
    onColor: (Int) -> Unit
) {
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.subscriptions_name_label)) },
        singleLine = true
    )
    Text(stringResource(R.string.subscriptions_color))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        EventColorChoice.entries.forEach { choice ->
            ColorSwatch(choice.argb, choice.argb == color, colorName(choice)) {
                onColor(choice.argb)
            }
        }
    }
}

@Composable
private fun problemText(problem: AddResult): String = stringResource(
    when (problem) {
        AddResult.Insecure -> R.string.subscriptions_insecure
        AddResult.Duplicate -> R.string.subscriptions_duplicate
        else -> R.string.subscriptions_invalid
    }
)
