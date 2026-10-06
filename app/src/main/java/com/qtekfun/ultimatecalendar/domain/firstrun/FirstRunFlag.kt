// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

/**
 * Whether the first-run wizard was already shown (RF-01). Kept this small so the settings
 * repository can take it over without touching the wizard.
 */
interface FirstRunFlag {
    fun isDone(): Boolean

    fun markDone()
}
