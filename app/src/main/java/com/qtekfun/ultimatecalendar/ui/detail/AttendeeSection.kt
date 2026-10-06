// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.AttendeeGroups
import com.qtekfun.ultimatecalendar.domain.detail.AttendeeRow
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.ui.components.SectionHeader
import com.qtekfun.ultimatecalendar.ui.theme.CalendarShapes
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

private val Avatar = 40.dp

/** The attendees: the organizer first, then the people grouped by their answer (RF-04). */
@Composable
internal fun AttendeeSection(attendees: AttendeeGroups, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionHeader(stringResource(R.string.detail_attendees, attendees.total))
        attendees.organizer?.let { organizer ->
            AttendeeLine(organizer, organizer.attendee.status, isOrganizer = true)
        }
        attendees.groups.forEach { group ->
            SectionHeader(stringResource(groupTitle(group.status), group.rows.size))
            group.rows.forEach { AttendeeLine(it, group.status, isOrganizer = false) }
        }
    }
}

@StringRes
private fun groupTitle(status: AttendeeStatus) = when (status) {
    AttendeeStatus.ACCEPTED -> R.string.detail_group_accepted
    AttendeeStatus.TENTATIVE -> R.string.detail_group_maybe
    AttendeeStatus.DECLINED -> R.string.detail_group_declined
    AttendeeStatus.NEEDS_ACTION -> R.string.detail_group_no_answer
}

@StringRes
private fun statusName(status: AttendeeStatus) = when (status) {
    AttendeeStatus.ACCEPTED -> R.string.detail_status_accepted
    AttendeeStatus.TENTATIVE -> R.string.detail_status_maybe
    AttendeeStatus.DECLINED -> R.string.detail_status_declined
    AttendeeStatus.NEEDS_ACTION -> R.string.detail_status_no_answer
}

/** One person. The user's own line is filled and says "You"; a screen reader hears the answer. */
@Composable
private fun AttendeeLine(row: AttendeeRow, status: AttendeeStatus, isOrganizer: Boolean) {
    val attendee = row.attendee
    val answer = stringResource(statusName(status))
    val summary = stringResource(
        R.string.detail_attendee_description,
        row.label,
        attendee.email,
        answer
    )
    val fill = if (row.isSelf) {
        Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CalendarShapes.card)
    } else {
        Modifier
    }
    val textColor = if (row.isSelf) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s)
            .then(fill)
            .heightIn(min = Dimens.minTouch)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) { contentDescription = summary },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Initial(row.label)
        Spacer(Modifier.width(Spacing.m))
        AttendeeText(row, isOrganizer, textColor, Modifier.weight(1f))
    }
}

@Composable
private fun AttendeeText(
    row: AttendeeRow,
    isOrganizer: Boolean,
    textColor: Color,
    modifier: Modifier
) {
    Column(modifier) {
        val you = if (row.isSelf) " · " + stringResource(R.string.detail_you) else ""
        Text(
            row.label + you,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (row.isSelf) FontWeight.Bold else null,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val second = listOfNotNull(
            row.attendee.email.takeIf { it != row.label },
            stringResource(R.string.detail_organizer).takeIf { isOrganizer }
        ).joinToString(" · ")
        if (second.isNotEmpty()) {
            Text(
                second,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.isSelf) {
                    textColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The first letter in a circle. Decorative: the name is read next to it. */
@Composable
private fun Initial(label: String) {
    Box(
        Modifier
            .size(Avatar)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/**
 * Accept, Maybe and Decline (RF-06). The current answer is the filled one; while one is being
 * sent all wait and the chosen one shows a spinner. They wrap to more lines on large fonts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResponseButtons(
    current: AttendeeStatus?,
    responding: AttendeeStatus?,
    onRespond: (AttendeeStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        Text(
            stringResource(R.string.detail_going),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.padding(top = Spacing.s)
        ) {
            ANSWERS.forEach { (status, label) ->
                ResponseButton(
                    label = stringResource(label),
                    selected = current == status,
                    sending = responding == status,
                    enabled = responding == null,
                    onClick = { onRespond(status) }
                )
            }
        }
    }
}

private val ANSWERS = listOf(
    AttendeeStatus.ACCEPTED to R.string.detail_accept,
    AttendeeStatus.TENTATIVE to R.string.detail_maybe,
    AttendeeStatus.DECLINED to R.string.detail_decline
)

@Composable
private fun ResponseButton(
    label: String,
    selected: Boolean,
    sending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val chosen = stringResource(R.string.detail_selected)
    val sendingText = stringResource(R.string.detail_sending_answer)
    val modifier = Modifier.heightIn(min = Dimens.minTouch).semantics {
        if (sending) {
            stateDescription = sendingText
        } else if (selected) {
            stateDescription = chosen
        }
    }
    val content: @Composable () -> Unit = {
        when {
            sending -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            selected -> Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp))
        }
        if (sending || selected) Spacer(Modifier.width(Spacing.s))
        Text(label)
    }
    if (selected || sending) {
        FilledTonalButton(onClick, modifier, enabled = enabled || sending) { content() }
    } else {
        OutlinedButton(onClick, modifier, enabled = enabled) { content() }
    }
}
