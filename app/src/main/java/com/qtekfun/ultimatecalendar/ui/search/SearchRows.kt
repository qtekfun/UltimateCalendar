// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.accessibility.EventSpeech
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.search.FieldMatch
import com.qtekfun.ultimatecalendar.domain.search.HighlightedText
import com.qtekfun.ultimatecalendar.domain.search.SearchField
import com.qtekfun.ultimatecalendar.domain.search.SearchResult
import com.qtekfun.ultimatecalendar.ui.components.CalendarColorDot
import com.qtekfun.ultimatecalendar.ui.components.SectionHeader
import com.qtekfun.ultimatecalendar.ui.components.rememberSpeechWords
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** How long the line that explains a match (the place, the attendee, the description) can be. */
private const val EXCERPT_LENGTH = 90

/** The text with the found words drawn bold on a soft background. */
@Composable
internal fun HighlightedText.toAnnotated(): AnnotatedString {
    val style = SpanStyle(
        fontWeight = FontWeight.Bold,
        background = MaterialTheme.colorScheme.tertiaryContainer,
        color = MaterialTheme.colorScheme.onTertiaryContainer
    )
    return buildAnnotatedString {
        segments().forEach {
            if (it.highlighted) withStyle(style) { append(it.text) } else append(it.text)
        }
    }
}

/** One found event: its calendar color, title and the line that explains why it matched. */
@Composable
internal fun SearchResultRow(
    result: SearchResult,
    color: Int,
    zone: ZoneId,
    onOpen: (EventInstance) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = result.match.title
    val untitled = stringResource(R.string.timegrid_untitled)
    val detail = result.match.detail?.let { detailLabel(it) }
    val moment = whenLabel(result.instance.time, zone)
    val spoken = listOfNotNull(
        title.text.ifBlank { untitled },
        moment,
        detail?.text,
        stringResource(R.string.search_repeats).takeIf { result.instance.isRecurring },
        EventSpeech.statusWord(result.instance.selfStatus, rememberSpeechWords())
    ).joinToString(", ")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clickable(
                onClickLabel = stringResource(R.string.agenda_open_event),
                role = Role.Button
            ) { onOpen(result.instance) }
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.Top
    ) {
        CalendarColorDot(color, Modifier.padding(top = Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(
                if (title.text.isBlank()) AnnotatedString(untitled) else title.toAnnotated(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                moment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** "Location: Harbour cafe", with the found words highlighted. */
@Composable
private fun detailLabel(match: FieldMatch): AnnotatedString {
    val label = stringResource(
        when (match.field) {
            SearchField.LOCATION -> R.string.search_field_location
            SearchField.ATTENDEE -> R.string.search_field_attendee
            else -> R.string.search_field_description
        }
    )
    val excerpt = HighlightedText.excerpt(match, EXCERPT_LENGTH).toAnnotated()
    return buildAnnotatedString {
        append(stringResource(R.string.search_snippet, label, ""))
        append(excerpt)
    }
}

/** "Oct 6, 2026, 9:00 AM", or the date and "All day". */
@Composable
private fun whenLabel(time: EventTime, zone: ZoneId): String {
    val locale = Locale.current.platformLocale
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val hours = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    return when (time) {
        is EventTime.AllDay ->
            "${time.startDate.format(dates)}, ${stringResource(R.string.cal_all_day)}"

        is EventTime.Timed -> {
            val start = time.start.atZone(zone)
            "${start.format(dates)}, ${start.format(hours)}"
        }
    }
}

/** The "Recent searches" header with its Clear button. */
@Composable
internal fun RecentHeader(onClear: () -> Unit, modifier: Modifier = Modifier) {
    val clearDescription = stringResource(R.string.search_recent_clear_description)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(stringResource(R.string.search_recent_title), Modifier.weight(1f))
        TextButton(
            onClick = onClear,
            modifier = Modifier.clearAndSetSemantics { contentDescription = clearDescription }
        ) { Text(stringResource(R.string.search_recent_clear)) }
    }
}

/** A recent search: tap to run it again, the cross to forget it. */
@Composable
internal fun RecentRow(
    query: String,
    onUse: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clickable(role = Role.Button, onClick = onUse)
            .padding(start = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            query,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onForget) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.search_recent_forget, query),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
