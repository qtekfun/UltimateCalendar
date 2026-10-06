// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** A message of the backup screen: a plain string, or a plural that says how many. */
sealed interface BackupMessage {
    data class Text(@StringRes val id: Int) : BackupMessage

    data class Count(@PluralsRes val id: Int, val count: Int) : BackupMessage
}
