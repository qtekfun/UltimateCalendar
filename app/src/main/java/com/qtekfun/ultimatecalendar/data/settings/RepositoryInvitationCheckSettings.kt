// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings

import com.qtekfun.ultimatecalendar.domain.settings.InviteCheckInterval
import com.qtekfun.ultimatecalendar.sync.CheckInterval
import com.qtekfun.ultimatecalendar.sync.InvitationCheckSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the invitation check reads from the settings: the interval and the user's aliases. */
@Singleton
class RepositoryInvitationCheckSettings @Inject constructor(
    private val repository: SettingsRepository
) : InvitationCheckSettings {
    override val intervals: Flow<CheckInterval> =
        repository.settings.map { it.inviteCheck.toCheckInterval() }.distinctUntilChanged()

    override suspend fun aliases(): Set<String> = repository.current().ownEmails.toSet()
}

internal fun InviteCheckInterval.toCheckInterval(): CheckInterval = when (this) {
    InviteCheckInterval.EVERY_15 -> CheckInterval.QUARTER_HOUR
    InviteCheckInterval.EVERY_30 -> CheckInterval.HALF_HOUR
    InviteCheckInterval.EVERY_60 -> CheckInterval.HOUR
    InviteCheckInterval.MANUAL -> CheckInterval.MANUAL_ONLY
}
