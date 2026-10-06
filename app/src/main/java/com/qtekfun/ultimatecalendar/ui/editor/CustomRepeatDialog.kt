// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.MonthlyDay
import com.qtekfun.ultimatecalendar.domain.editor.RepeatSummary
import com.qtekfun.ultimatecalendar.domain.recurrence.CustomRepeat
import com.qtekfun.ultimatecalendar.domain.recurrence.Frequency
import com.qtekfun.ultimatecalendar.domain.recurrence.MonthlyMode
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatEnd
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields

private const val MAX_DIGITS = 3
private val FieldWidth = 96.dp

/**
 * The custom repetition editor (RF-05): every N days, weeks, months or years; on which weekdays,
 * or on a day or "the 3rd Friday" of the month; ending never, on a date or after N times. A
 * sentence at the bottom says what the rule means. The rule itself is [CustomRepeat].
 */
@Composable
internal fun CustomRepeatDialog(
    initial: CustomRepeat,
    anchor: LocalDate,
    onDone: (CustomRepeat) -> Unit,
    onDismiss: () -> Unit
) {
    var repeat by remember { mutableStateOf(initial) }
    var showUntilPicker by remember { mutableStateOf(false) }
    val untilBeforeStart = repeat.end == RepeatEnd.ON_DATE &&
        repeat.until?.isBefore(anchor) == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_custom_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                IntervalField(repeat) { repeat = it }
                if (repeat.frequency == Frequency.WEEKLY) WeekdayChips(repeat) { repeat = it }
                if (repeat.frequency ==
                    Frequency.MONTHLY
                ) {
                    MonthlyChoices(repeat, anchor) { repeat = it }
                }
                EndChoices(repeat, { repeat = it }, { showUntilPicker = true })
                if (untilBeforeStart) {
                    ProblemText(
                        stringResource(R.string.editor_issue_repeat_before_start)
                    )
                }
                Text(
                    summaryText(RepeatSummary.of(repeat, anchor)),
                    modifier = Modifier.padding(top = Spacing.l),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !untilBeforeStart,
                onClick = {
                    onDone(repeat)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.editor_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        }
    )
    if (showUntilPicker) {
        EditorDatePicker(
            initial = repeat.until ?: anchor,
            onPick = { repeat = repeat.copy(until = it) },
            onDismiss = { showUntilPicker = false }
        )
    }
}

@Composable
private fun IntervalField(repeat: CustomRepeat, onChange: (CustomRepeat) -> Unit) {
    var text by remember { mutableStateOf(repeat.interval.toString()) }
    Text(stringResource(R.string.editor_custom_every), style = MaterialTheme.typography.labelLarge)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                text = typed.filter(Char::isDigit).take(MAX_DIGITS)
                text.toIntOrNull()?.takeIf { it >= 1 }?.let { onChange(repeat.copy(interval = it)) }
            },
            label = { Text(stringResource(R.string.editor_custom_interval)) },
            isError = text.toIntOrNull()?.let { it < 1 } != false,
            singleLine = true,
            keyboardOptions = numberKeyboard,
            modifier = Modifier.width(FieldWidth)
        )
        val unit = unitName(repeat.frequency, repeat.interval)
        ChoiceMenuRow(
            description = stringResource(R.string.editor_custom_unit) + ": " + unit,
            text = unit,
            options = Frequency.entries,
            optionName = { unitName(it, repeat.interval) },
            onPick = { onChange(repeat.copy(frequency = it)) }
        )
    }
}

@Composable
private fun unitName(frequency: Frequency, count: Int): String = pluralStringResource(
    when (frequency) {
        Frequency.DAILY -> R.plurals.editor_unit_day
        Frequency.WEEKLY -> R.plurals.editor_unit_week
        Frequency.MONTHLY -> R.plurals.editor_unit_month
        Frequency.YEARLY -> R.plurals.editor_unit_year
    },
    count
)

/** The weekdays of a weekly rule, starting at the locale's first day of the week. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(repeat: CustomRepeat, onChange: (CustomRepeat) -> Unit) {
    val first = WeekFields.of(currentLocale()).firstDayOfWeek
    val days = DayOfWeek.entries.let { all -> all.drop(first.ordinal) + all.take(first.ordinal) }
    Text(
        stringResource(R.string.editor_custom_on_days),
        modifier = Modifier.padding(top = Spacing.m),
        style = MaterialTheme.typography.labelLarge
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        days.forEach { day ->
            val selected = day in repeat.weekdays
            val fullName = weekdayName(day)
            FilterChip(
                selected = selected,
                onClick = {
                    // At least one day stays picked: an empty set would mean the event's own day.
                    val next = if (selected) repeat.weekdays - day else repeat.weekdays + day
                    if (next.isNotEmpty()) onChange(repeat.copy(weekdays = next))
                },
                label = { Text(weekdayName(day, TextStyle.SHORT)) },
                modifier = Modifier.semantics { contentDescription = fullName }
            )
        }
    }
}

/** A monthly rule falls on the day number, or on the n-th (or last) weekday of the event's date. */
@Composable
private fun MonthlyChoices(
    repeat: CustomRepeat,
    anchor: LocalDate,
    onChange: (CustomRepeat) -> Unit
) {
    Column(Modifier.selectableGroup().padding(top = Spacing.s)) {
        RepeatSummary.monthlyOptions(anchor).forEach { option ->
            val selected = when (option) {
                is MonthlyDay.OfMonth -> repeat.monthlyMode == MonthlyMode.DAY_OF_MONTH

                is MonthlyDay.Nth ->
                    repeat.monthlyMode == MonthlyMode.WEEKDAY_OF_MONTH &&
                        repeat.ordinal == option.ordinal
            }
            ChoiceRadio(monthlyDayText(option), selected) {
                onChange(
                    when (option) {
                        is MonthlyDay.OfMonth -> repeat.copy(monthlyMode = MonthlyMode.DAY_OF_MONTH)

                        is MonthlyDay.Nth -> repeat.copy(
                            monthlyMode = MonthlyMode.WEEKDAY_OF_MONTH,
                            ordinal = option.ordinal,
                            weekday = option.weekday
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EndChoices(
    repeat: CustomRepeat,
    onChange: (CustomRepeat) -> Unit,
    onPickDate: () -> Unit
) {
    Text(
        stringResource(R.string.editor_custom_ends),
        modifier = Modifier.padding(top = Spacing.m),
        style = MaterialTheme.typography.labelLarge
    )
    Column(Modifier.selectableGroup()) {
        ChoiceRadio(stringResource(R.string.editor_end_never), repeat.end == RepeatEnd.NEVER) {
            onChange(repeat.copy(end = RepeatEnd.NEVER))
        }
        ChoiceRadio(
            stringResource(R.string.editor_end_on) +
                repeat.until?.let { ": " + formatDate(it) }.orEmpty(),
            repeat.end == RepeatEnd.ON_DATE
        ) {
            onChange(repeat.copy(end = RepeatEnd.ON_DATE))
            onPickDate()
        }
        ChoiceRadio(
            stringResource(R.string.editor_end_after) + ": " +
                pluralStringResource(R.plurals.editor_times, repeat.count, repeat.count),
            repeat.end == RepeatEnd.AFTER_COUNT
        ) { onChange(repeat.copy(end = RepeatEnd.AFTER_COUNT)) }
    }
    if (repeat.end == RepeatEnd.AFTER_COUNT) CountField(repeat, onChange)
}

@Composable
private fun CountField(repeat: CustomRepeat, onChange: (CustomRepeat) -> Unit) {
    var text by remember { mutableStateOf(repeat.count.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed.filter(Char::isDigit).take(MAX_DIGITS)
            text.toIntOrNull()?.takeIf { it >= 1 }?.let { onChange(repeat.copy(count = it)) }
        },
        label = { Text(stringResource(R.string.editor_end_count)) },
        isError = text.toIntOrNull()?.let { it < 1 } != false,
        singleLine = true,
        keyboardOptions = numberKeyboard,
        modifier = Modifier.padding(top = Spacing.xs).width(FieldWidth * 2)
    )
}

@Composable
private fun ChoiceRadio(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(end = Spacing.m)
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private val numberKeyboard =
    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
