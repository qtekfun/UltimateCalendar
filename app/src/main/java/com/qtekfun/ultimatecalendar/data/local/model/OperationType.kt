// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.model

/** What a queued operation does to its event; the queue decides what each one may absorb. */
enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    RESPOND,
    EDIT_INSTANCE,
    CANCEL_INSTANCE
}
