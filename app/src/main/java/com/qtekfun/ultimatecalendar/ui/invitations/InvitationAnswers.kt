// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.invitations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAnswer
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

internal fun InvitationAnswer.label(): Int = when (this) {
    InvitationAnswer.ACCEPT -> R.string.invitation_accept
    InvitationAnswer.MAYBE -> R.string.invitation_maybe
    InvitationAnswer.DECLINE -> R.string.invitation_decline
}

/**
 * The answers as actions of the card, so a screen reader user can answer from the actions menu
 * without walking through the three buttons of every card.
 */
internal fun answerActions(
    labels: Map<InvitationAnswer, String>,
    onAnswer: (InvitationAnswer) -> Unit
): List<CustomAccessibilityAction> = InvitationAnswer.entries.map { answer ->
    CustomAccessibilityAction(labels.getValue(answer)) {
        onAnswer(answer)
        true
    }
}

/** Accept, Maybe and Decline; each says which event it answers ("Accept: Project kickoff"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnswerButtons(
    title: String,
    labels: Map<InvitationAnswer, String>,
    onAnswer: (InvitationAnswer) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        InvitationAnswer.entries.forEach { answer ->
            val label = labels.getValue(answer)
            val spoken = stringResource(R.string.invitation_answer_for, label, title)
            TextButton(
                onClick = { onAnswer(answer) },
                modifier = Modifier
                    .heightIn(min = Dimens.minTouch)
                    .semantics { contentDescription = spoken }
            ) { Text(label) }
        }
    }
}
