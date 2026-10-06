// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.invitations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAnswer
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationDay
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationTimeText
import com.qtekfun.ultimatecalendar.domain.invitations.detailRef
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.ui.components.CalendarSnackbarHost
import com.qtekfun.ultimatecalendar.ui.components.ComponentPreviews
import com.qtekfun.ultimatecalendar.ui.components.EmptyState
import com.qtekfun.ultimatecalendar.ui.components.EventListSkeleton
import com.qtekfun.ultimatecalendar.ui.components.PreviewSurface
import com.qtekfun.ultimatecalendar.ui.components.showUndo
import com.qtekfun.ultimatecalendar.ui.theme.CalendarShapes
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The invitation tray (RF-06): every pending invitation of every calendar, soonest first and by
 * day, each with Accept, Maybe and Decline, and a tap that opens its detail. Pull down to look
 * for new ones. An answer can be undone from the snackbar.
 */
@Composable
fun InvitationsScreen(
    onBack: () -> Unit,
    onOpen: (EventRef) -> Unit,
    viewModel: InvitationsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(R.string.cal_undo)
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InvitationsEvent.Answered -> {
                    val text = resources.getString(event.answer.message(), event.invitation.title)
                    if (snackbar.showUndo(text, undo)) viewModel.undo(event.invitation)
                }

                InvitationsEvent.AnswerFailed ->
                    snackbar.showSnackbar(resources.getString(R.string.invitation_answer_failed))

                InvitationsEvent.Gone ->
                    snackbar.showSnackbar(resources.getString(R.string.invitations_gone))

                InvitationsEvent.RefreshFailed ->
                    snackbar.showSnackbar(resources.getString(R.string.invitations_refresh_failed))
            }
        }
    }
    val actions = remember(viewModel, onBack, onOpen) {
        InvitationsActions(onBack, onOpen, viewModel::answer, viewModel::refresh)
    }
    InvitationsContent(state, snackbar, actions)
}

private fun InvitationAnswer.message(): Int = when (this) {
    InvitationAnswer.ACCEPT -> R.string.invitations_answered_accept
    InvitationAnswer.MAYBE -> R.string.invitations_answered_maybe
    InvitationAnswer.DECLINE -> R.string.invitations_answered_decline
}

/** What the tray can ask of whoever shows it. */
internal class InvitationsActions(
    val onBack: () -> Unit,
    val onOpen: (EventRef) -> Unit,
    val onAnswer: (Invitation, InvitationAnswer) -> Unit,
    val onRefresh: () -> Unit
) {
    companion object {
        val None = InvitationsActions({}, {}, { _, _ -> }, {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InvitationsContent(
    state: InvitationsUiState,
    snackbar: SnackbarHostState,
    actions: InvitationsActions
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shell_invitations)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.shell_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { CalendarSnackbarHost(snackbar) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            when {
                state.loading -> EventListSkeleton()
                state.days.isEmpty() -> EmptyTray()
                else -> InvitationList(state.days, actions.onOpen, actions.onAnswer)
            }
        }
    }
}

/** Scrollable even when empty, so pull to refresh works on the empty state. */
@Composable
private fun EmptyTray() {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            EmptyState(
                title = stringResource(R.string.invitations_empty_title),
                body = stringResource(R.string.invitations_empty_body),
                modifier = Modifier.padding(top = Spacing.xxl)
            )
        }
    }
}

@Composable
private fun InvitationList(
    days: List<InvitationDay>,
    onOpen: (EventRef) -> Unit,
    onAnswer: (Invitation, InvitationAnswer) -> Unit
) {
    val locale = LocalLocale.current.platformLocale
    val zone = ZoneId.systemDefault()
    val dayFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = Spacing.l,
            vertical = Spacing.s
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        days.forEach { day ->
            item(key = "day-${day.date}") {
                Text(
                    dayFormat.format(day.date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = Spacing.m, bottom = Spacing.xs)
                        .semantics { heading() }
                )
            }
            items(day.invitations, key = { it.key.toString() }) { invitation ->
                InvitationCard(
                    invitation = invitation,
                    whenText = InvitationTimeText.format(invitation.time, zone, locale),
                    onOpen = { onOpen(invitation.detailRef()) },
                    onAnswer = { onAnswer(invitation, it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvitationCard(
    invitation: Invitation,
    whenText: String,
    onOpen: () -> Unit,
    onAnswer: (InvitationAnswer) -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch),
        shape = CalendarShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            Modifier.padding(
                start = Spacing.l,
                end = Spacing.s,
                top = Spacing.m,
                bottom = Spacing.s
            )
        ) {
            Text(
                invitation.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                whenText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            invitation.location?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            invitation.organizer?.let {
                Text(
                    stringResource(R.string.invitations_from, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                InvitationAnswer.entries.forEach { answer ->
                    TextButton(
                        onClick = { onAnswer(answer) },
                        modifier = Modifier.heightIn(min = Dimens.minTouch)
                    ) { Text(stringResource(answer.label())) }
                }
            }
        }
    }
}

private fun InvitationAnswer.label(): Int = when (this) {
    InvitationAnswer.ACCEPT -> R.string.invitation_accept
    InvitationAnswer.MAYBE -> R.string.invitation_maybe
    InvitationAnswer.DECLINE -> R.string.invitation_decline
}

private fun sample(
    id: Long,
    title: String,
    start: String,
    organizer: String?,
    place: String?,
    calendar: Long = 1
) = Invitation(
    key = InvitationKey(CalendarId(calendar), EventId(id)),
    title = title,
    time = Instant.parse(start).let {
        EventTime.Timed(it, it.plusSeconds(HOUR), ZoneOffset.UTC)
    },
    location = place,
    organizer = organizer
)

private const val HOUR = 3_600L

private val previewDays = listOf(
    InvitationDay(
        LocalDate.parse("2026-06-10"),
        listOf(
            sample(1, "Project kickoff", "2026-06-10T09:00:00Z", "ana@example.org", "Room 4"),
            sample(2, "Lunch with the team", "2026-06-10T12:30:00Z", "bo@example.org", null)
        )
    ),
    InvitationDay(
        LocalDate.parse("2026-06-12"),
        listOf(
            sample(2, "Board game night", "2026-06-12T18:00:00Z", null, "Casa de Ana", calendar = 2)
        )
    )
)

@ComponentPreviews
@Composable
internal fun InvitationsContentPreview() {
    PreviewSurface {
        InvitationsContent(
            state = InvitationsUiState(days = previewDays, loading = false),
            snackbar = remember { SnackbarHostState() },
            actions = InvitationsActions.None
        )
    }
}

@ComponentPreviews
@Composable
internal fun InvitationsEmptyPreview() {
    PreviewSurface {
        InvitationsContent(
            state = InvitationsUiState(loading = false),
            snackbar = remember { SnackbarHostState() },
            actions = InvitationsActions.None
        )
    }
}
