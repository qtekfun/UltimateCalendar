// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.EventDetail
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.ui.components.CalendarColorDot
import com.qtekfun.ultimatecalendar.ui.components.SectionHeader
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/**
 * Everything the event detail shows (RF-04), top to bottom: title, when, repetition, place and
 * video call, answer buttons, reminders, calendar, description and attendees. A part with
 * nothing to say is left out.
 */
@Composable
internal fun DetailBody(
    detail: EventDetail,
    words: DetailWords,
    responding: AttendeeStatus?,
    actions: DetailActions,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Title(detail)
        WhenRow(detail, words)
        if (detail.repeat.isNotEmpty()) {
            DetailRow(leading = { RowIcon(Icons.Filled.Refresh) }) {
                Text(words.repeat(detail.repeat), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.detail_series_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Place(detail, actions)
        if (detail.joinUrl != null) {
            FilledTonalButton(
                onClick = { actions.onOpenUri(detail.joinUrl) },
                modifier = Modifier
                    .padding(horizontal = Spacing.l, vertical = Spacing.s)
                    .padding(start = Dimens.minTouch)
                    .heightIn(min = Dimens.minTouch)
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Text(
                    stringResource(R.string.detail_join),
                    modifier = Modifier.padding(start = Spacing.s)
                )
            }
        }
        if (detail.canRespond) {
            ResponseButtons(detail.self?.status, responding, actions.onRespond)
        }
        if (detail.reminders.isNotEmpty()) {
            DetailRow(leading = { RowIcon(Icons.Filled.Notifications) }) {
                detail.reminders.forEach {
                    Text(words.reminder(it), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        CalendarLine(detail)
        Description(detail, actions)
        detail.attendees?.let { AttendeeSection(it, Modifier.padding(top = Spacing.s)) }
    }
}

@Composable
private fun Title(detail: EventDetail) {
    DetailRow(leading = {
        CalendarColorDot(
            detail.color ?: MaterialTheme.colorScheme.primary.toArgb(),
            size = Dimens.dotLarge
        )
    }) {
        Text(
            detail.title.ifBlank { stringResource(R.string.detail_no_title) },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun Place(detail: EventDetail, actions: DetailActions) {
    val location = detail.location ?: return
    val target = detail.mapUri ?: detail.locationUrl
    DetailRow(
        leading = { RowIcon(Icons.Filled.LocationOn) },
        onClick = target?.let { uri -> { actions.onOpenUri(uri) } },
        onClickLabel = stringResource(
            if (detail.mapUri != null) R.string.detail_open_map else R.string.detail_open_link
        )
    ) {
        Text(location, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CalendarLine(detail: EventDetail) {
    val calendar = detail.calendar ?: return
    DetailRow(leading = { CalendarColorDot(detail.color ?: calendar.color) }) {
        Text(
            stringResource(
                R.string.detail_calendar_line,
                calendar.displayName,
                calendar.account.name
            ),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** The description, selectable, with its links tappable. */
@Composable
private fun Description(detail: EventDetail, actions: DetailActions) {
    val text = detail.description ?: return
    val style = TextLinkStyles(
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        )
    )
    val annotated = buildAnnotatedString {
        append(text)
        detail.descriptionLinks.forEach { link ->
            addLink(
                LinkAnnotation.Url(link.url, style) { actions.onOpenUri(link.url) },
                link.start,
                link.end
            )
        }
    }
    SectionHeader(stringResource(R.string.detail_description))
    SelectionContainer(Modifier.padding(horizontal = Spacing.l)) {
        Text(
            annotated,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
private fun WhenRow(detail: EventDetail, words: DetailWords) {
    DetailRow(leading = { RowIcon(Icons.Filled.DateRange) }) {
        words.whenLines(detail.time).forEachIndexed { index, line ->
            Text(
                line,
                style = if (index == 0) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (index == 0) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
