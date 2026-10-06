// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.BuildConfig
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindOption
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.settings.FirstDayOfWeek
import com.qtekfun.ultimatecalendar.domain.settings.InitialView
import com.qtekfun.ultimatecalendar.domain.settings.InviteCheckInterval
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules
import com.qtekfun.ultimatecalendar.ui.account.AccountsSection
import com.qtekfun.ultimatecalendar.ui.adaptive.ReadingPane
import com.qtekfun.ultimatecalendar.ui.firstrun.LocalOpenWizard

/**
 * Settings (RF-10). The default calendar can be picked from the ones that accept events; the row
 * is left out while there are none. Thin: the rules are in
 * [SettingsRules] and the repository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value
    val calendars = viewModel.calendars.collectAsStateWithLifecycle().value
    // The first-run wizard (RF-01) again: permissions, battery, accounts.
    val openWizard = LocalOpenWizard.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionTitle(stringResource(R.string.settings_accounts))
                AccountsSection(onOpenAccount)
                SectionTitle(stringResource(R.string.settings_appearance))
                AppearanceSection(settings, viewModel)
                SectionTitle(stringResource(R.string.settings_calendar))
                CalendarSection(settings, calendars, viewModel)
                SectionTitle(stringResource(R.string.settings_reminders))
                RemindersSection(settings, viewModel)
                SectionTitle(stringResource(R.string.settings_invitations))
                InvitationsSection(settings, viewModel)
                SectionTitle(stringResource(R.string.settings_subscriptions))
                SubscriptionsSection()
                SectionTitle(stringResource(R.string.settings_backup))
                BackupSection()
                SectionTitle(stringResource(R.string.settings_setup))
                SettingsCard {
                    ActionRow(
                        stringResource(R.string.settings_setup_open),
                        stringResource(R.string.settings_setup_hint),
                        openWizard
                    )
                }
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsCard {
        ChoiceRow(
            stringResource(R.string.settings_theme),
            themeName(settings.theme),
            ThemeMode.entries,
            { themeName(it) }
        ) { picked -> viewModel.update { it.copy(theme = picked) } }
        HorizontalDivider()
        SwitchRow(
            stringResource(R.string.settings_amoled),
            stringResource(R.string.settings_amoled_hint),
            settings.amoled
        ) { on -> viewModel.update { it.copy(amoled = on) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            HorizontalDivider()
            SwitchRow(
                stringResource(R.string.settings_dynamic_color),
                null,
                settings.dynamicColor
            ) { on -> viewModel.update { it.copy(dynamicColor = on) } }
        }
    }
}

@Composable
private fun CalendarSection(
    settings: AppSettings,
    calendars: List<CalendarInfo>,
    viewModel: SettingsViewModel
) {
    SettingsCard {
        ChoiceRow(
            stringResource(R.string.settings_first_day),
            firstDayName(settings.firstDayOfWeek),
            FirstDayOfWeek.entries,
            { firstDayName(it) }
        ) { picked -> viewModel.update { it.copy(firstDayOfWeek = picked) } }
        HorizontalDivider()
        ChoiceRow(
            stringResource(R.string.settings_initial_view),
            viewName(settings.initialView),
            InitialView.entries,
            { viewName(it) }
        ) { picked -> viewModel.update { it.copy(initialView = picked) } }
        val writable = calendars.filter { it.access.canCreate }
        if (writable.isNotEmpty()) {
            HorizontalDivider()
            val auto = stringResource(R.string.settings_default_calendar_auto)
            ChoiceRow(
                stringResource(R.string.settings_default_calendar),
                writable.firstOrNull { it.id == settings.defaultCalendar }?.displayName ?: auto,
                listOf<CalendarInfo?>(null) + writable,
                { it?.displayName ?: auto }
            ) { picked -> viewModel.update { it.copy(defaultCalendar = picked?.id) } }
        }
        HorizontalDivider()
        ChoiceRow(
            stringResource(R.string.settings_default_duration),
            durationName(settings.defaultDurationMinutes),
            SettingsRules.DURATION_CHOICES,
            { durationName(it) }
        ) { picked -> viewModel.update { it.copy(defaultDurationMinutes = picked) } }
    }
}

@Composable
private fun RemindersSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsCard {
        RemindersEditor(
            stringResource(R.string.settings_default_reminders),
            settings.defaultReminders
        ) { offsets -> viewModel.update { it.copy(defaultReminders = offsets) } }
        HorizontalDivider()
        RemindersEditor(
            stringResource(R.string.settings_default_all_day_reminders),
            settings.defaultAllDayReminders
        ) { offsets -> viewModel.update { it.copy(defaultAllDayReminders = offsets) } }
        HorizontalDivider()
        ChoiceRow(
            stringResource(R.string.settings_all_day_time),
            timeOfDayName(settings.allDayMinute),
            SettingsRules.ALL_DAY_TIME_CHOICES,
            { timeOfDayName(it) }
        ) { picked -> viewModel.update { it.copy(allDayMinute = picked) } }
        HorizontalDivider()
        ChoiceRow(
            stringResource(R.string.settings_missed_window),
            missedWindowName(settings.missedWindowHours),
            SettingsRules.MISSED_WINDOW_CHOICES,
            { missedWindowName(it) },
            hint = stringResource(R.string.settings_missed_window_hint)
        ) { picked -> viewModel.update { it.copy(missedWindowHours = picked) } }
        HorizontalDivider()
        SwitchRow(
            stringResource(R.string.settings_alarm_mode),
            stringResource(R.string.settings_alarm_mode_hint),
            settings.alarmClock
        ) { on -> viewModel.update { it.copy(alarmClock = on) } }
        HorizontalDivider()
        SwitchRow(
            stringResource(R.string.settings_robust_mode),
            stringResource(R.string.settings_robust_mode_hint),
            settings.robustMode
        ) { on -> viewModel.update { it.copy(robustMode = on) } }
    }
}

@Composable
private fun InvitationsSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsCard {
        ChoiceRow(
            stringResource(R.string.settings_invite_check),
            inviteCheckName(settings.inviteCheck),
            InviteCheckInterval.entries,
            { inviteCheckName(it) }
        ) { picked -> viewModel.update { it.copy(inviteCheck = picked) } }
        HorizontalDivider()
        SwitchRow(
            stringResource(R.string.settings_notify_changes),
            null,
            settings.notifyChanges
        ) { on -> viewModel.update { it.copy(notifyChanges = on) } }
        HorizontalDivider()
        SwitchRow(
            stringResource(R.string.settings_notify_cancellations),
            null,
            settings.notifyCancellations
        ) { on -> viewModel.update { it.copy(notifyCancellations = on) } }
        HorizontalDivider()
        ChoiceRow(
            stringResource(R.string.settings_re_remind),
            reRemindName(settings.reRemind),
            ReRemindOption.entries,
            { reRemindName(it) },
            hint = stringResource(R.string.settings_re_remind_hint)
        ) { picked -> viewModel.update { it.copy(reRemind = picked) } }
        HorizontalDivider()
        OwnEmailsEditor(
            settings.ownEmails,
            onAdd = viewModel::addOwnEmail,
            onRemove = { address ->
                viewModel.update { it.copy(ownEmails = it.ownEmails - address) }
            }
        )
    }
}
