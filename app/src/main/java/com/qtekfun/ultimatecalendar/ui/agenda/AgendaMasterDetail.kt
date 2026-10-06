// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.agenda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.ui.adaptive.ReadingPane
import com.qtekfun.ultimatecalendar.ui.components.EmptyState
import com.qtekfun.ultimatecalendar.ui.theme.Dimens

/**
 * The Agenda on a wide window: the list on the left and, on the right, whatever [detail] draws
 * for the [selected] occurrence, or a hint to pick one. [list] gets the modifier that sizes it.
 * The detail is not part of this file: it is the same screen the phone opens full screen.
 */
@Composable
fun AgendaMasterDetail(
    selected: EventRef?,
    list: @Composable (Modifier) -> Unit,
    detail: @Composable (EventRef) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        list(Modifier.width(Dimens.listPaneWidth).fillMaxHeight())
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight(), Alignment.Center) {
            if (selected == null) {
                EmptyState(
                    title = stringResource(R.string.agenda_pick_title),
                    body = stringResource(R.string.agenda_pick_body)
                )
            } else {
                ReadingPane { detail(selected) }
            }
        }
    }
}
