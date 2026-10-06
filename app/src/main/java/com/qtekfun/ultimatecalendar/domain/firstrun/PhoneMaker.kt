// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

/**
 * Phone makers whose system stops apps in the background (dontkillmyapp.com), each with its own
 * settings to allow reminders (RF-01, RF-08). [OTHER] gets the general advice.
 */
enum class PhoneMaker {
    COLOROS,
    XIAOMI,
    HUAWEI,
    SAMSUNG,
    VIVO,
    OTHER;

    companion object {
        /** From `Build.MANUFACTURER`. */
        fun of(manufacturer: String): PhoneMaker = when (manufacturer.trim().lowercase()) {
            "oppo", "realme", "oneplus" -> COLOROS
            "xiaomi", "redmi", "poco" -> XIAOMI
            "huawei", "honor" -> HUAWEI
            "samsung" -> SAMSUNG
            "vivo", "iqoo" -> VIVO
            else -> OTHER
        }
    }
}
