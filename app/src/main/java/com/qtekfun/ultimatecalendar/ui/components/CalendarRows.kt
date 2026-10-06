// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.ui.theme.ColorMath
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/** A calendar's color as a filled circle. Decorative: the name next to it says the rest. */
@Composable
fun CalendarColorDot(color: Int, modifier: Modifier = Modifier, size: Dp = Dimens.dot) {
    Spacer(modifier.size(size).clip(CircleShape).background(Color(color)))
}

/**
 * A calendar with its color and a visible/hidden checkbox, as in Google Calendar's drawer. The
 * checkbox is filled with the calendar's color; its tick is black or white by contrast. The whole
 * row is one 48 dp toggle.
 */
@Composable
fun CalendarCheckRow(
    name: String,
    color: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val fill = Color(color)
    val state =
        stringResource(if (checked) R.string.cal_state_visible else R.string.cal_state_hidden)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { stateDescription = state }
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = fill,
                uncheckedColor = fill,
                checkmarkColor = Color(ColorMath.onColor(color))
            )
        )
        Spacer(Modifier.width(Spacing.l))
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A small heading above a group of rows ("Calendars", an account, "Tomorrow"). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .semantics { heading() }
    )
}

@ComponentPreviews
@Composable
internal fun CalendarRowsPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader("alex@example.org")
            CalendarCheckRow("Personal", 0xFF1A73E8.toInt(), checked = true, onCheckedChange = {})
            CalendarCheckRow("Birthdays", 0xFFF6BF26.toInt(), checked = true, onCheckedChange = {})
            CalendarCheckRow("Holidays and long name that wraps", 0xFF0B8043.toInt(), false, {})
            Row(Modifier.padding(Spacing.l)) { CalendarColorDot(0xFFD50000.toInt()) }
        }
    }
}
