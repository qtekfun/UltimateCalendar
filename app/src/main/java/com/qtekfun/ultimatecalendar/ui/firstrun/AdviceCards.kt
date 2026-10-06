// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.firstrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.firstrun.PhoneMaker
import com.qtekfun.ultimatecalendar.domain.firstrun.TestDelivery
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

private const val CHECK_EVERY_MS = 5_000L

@Composable
internal fun StepSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

/**
 * What to change in the maker's own settings, which no app can do for the user: exact steps,
 * and a button to the maker's screen or, where the system closes it to apps, the app's info page.
 */
@Composable
internal fun MakerCard(maker: PhoneMaker) {
    val context = LocalContext.current
    WizardCard(
        R.string.wizard_maker,
        makerAdvice(maker),
        action = Action(R.string.wizard_open_maker_settings) {
            PhoneSettings.openMakerSettings(context, maker)
        }
    )
}

private fun makerAdvice(maker: PhoneMaker): Int = when (maker) {
    PhoneMaker.COLOROS -> R.string.wizard_maker_coloros
    PhoneMaker.XIAOMI -> R.string.wizard_maker_xiaomi
    PhoneMaker.HUAWEI -> R.string.wizard_maker_huawei
    PhoneMaker.SAMSUNG -> R.string.wizard_maker_samsung
    PhoneMaker.VIVO -> R.string.wizard_maker_vivo
    PhoneMaker.OTHER -> R.string.wizard_maker_other
}

/** Other calendar apps that also remind of events, each with a button to its notifications. */
@Composable
internal fun OtherAppsCard(packages: List<String>, viewModel: FirstRunViewModel) {
    val context = LocalContext.current
    StepSurface {
        Text(
            stringResource(R.string.wizard_other_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            stringResource(R.string.wizard_other_why),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        packages.forEach { name ->
            OutlinedButton(
                onClick = { PhoneSettings.openOtherAppNotifications(context, name) }
            ) {
                Text(stringResource(R.string.wizard_other_open, viewModel.label(name)))
            }
        }
    }
}

/**
 * A real reminder in a minute, set like any other, and how it went: on time, late by how much,
 * or not arrived. While waiting it checks every few seconds.
 */
@Composable
internal fun TestReminderCard(viewModel: FirstRunViewModel) {
    val delivery by viewModel.delivery.collectAsStateWithLifecycle()
    LaunchedEffect(delivery) {
        if (delivery is TestDelivery.Waiting) {
            delay(CHECK_EVERY_MS)
            viewModel.checkTest()
        }
    }
    val title = stringResource(R.string.wizard_test_notification)
    StepSurface {
        Text(
            stringResource(R.string.wizard_test_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            stringResource(R.string.wizard_test_why),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = { viewModel.sendTest(title) }) {
            Text(stringResource(R.string.wizard_test_button))
        }
        delivery?.let {
            val problem = it is TestDelivery.Late || it is TestDelivery.Missing
            Text(
                deliveryText(it),
                style = MaterialTheme.typography.bodySmall,
                color = if (problem) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun deliveryText(delivery: TestDelivery): String = when (delivery) {
    is TestDelivery.Waiting -> stringResource(R.string.test_waiting, time(delivery.scheduledAt))

    is TestDelivery.OnTime -> stringResource(R.string.test_on_time, time(delivery.arrivedAt))

    is TestDelivery.Late -> pluralStringResource(
        R.plurals.test_late,
        delivery.minutes.toInt(),
        time(delivery.arrivedAt),
        delivery.minutes.toInt()
    )

    is TestDelivery.Missing -> stringResource(R.string.test_missing, time(delivery.scheduledAt))
}

private fun time(at: Instant): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at.atZone(ZoneId.systemDefault()))
