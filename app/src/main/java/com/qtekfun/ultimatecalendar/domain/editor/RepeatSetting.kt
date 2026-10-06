// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.recurrence.CustomRepeat
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceRules
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatPreset
import java.time.LocalDate
import java.time.ZoneId

/** What the Repeat field of the editor holds: a preset, a custom rule or a rule left as it is. */
sealed interface RepeatSetting {
    /** One of the quick choices of the Repeat menu ([RepeatPreset.NEVER] included). */
    data class Preset(val preset: RepeatPreset) : RepeatSetting

    /** A rule built in the custom editor. */
    data class Custom(val repeat: CustomRepeat) : RepeatSetting

    /**
     * A rule the app does not understand (see `RecurrenceRules.parse`). It is shown but not
     * edited, and saved exactly as it came so that nothing is lost.
     */
    data class Unsupported(val rrule: String) : RepeatSetting

    val repeats: Boolean get() = this != NEVER

    /**
     * The RRULE to store for an event that starts on [anchor], or null if it does not repeat.
     * [zone] is the event's zone for timed events and null for all-day ones, which decides how
     * the end date of the rule is written.
     */
    fun toRrule(anchor: LocalDate, zone: ZoneId?): String? = when (this) {
        is Preset -> preset.rule
        is Custom -> RecurrenceRules.format(repeat.toRule(anchor, zone))
        is Unsupported -> rrule
    }

    companion object {
        val NEVER: RepeatSetting = Preset(RepeatPreset.NEVER)

        /** The setting that stands for [rrule], of an event starting on [anchor] in [zone]. */
        fun of(rrule: String?, anchor: LocalDate, zone: ZoneId): RepeatSetting {
            val preset = RepeatPreset.of(rrule)
            val rule = rrule?.let { RecurrenceRules.parse(it) }
            return when {
                preset != null -> Preset(preset)
                rule != null -> Custom(CustomRepeat.from(rule, anchor, zone))
                else -> Unsupported(requireNotNull(rrule))
            }
        }
    }
}
