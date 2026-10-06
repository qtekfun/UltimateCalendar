// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/** The width of the icon column: icon, then the text lines up under the title. */
private val IconColumn = 24.dp

/**
 * A line of the detail: a [leading] mark (an icon or a color dot, decorative: the text says the
 * rest) and its [content]. With [onClick] the whole row is one 48 dp target described by
 * [onClickLabel].
 */
@Composable
internal fun DetailRow(
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickable = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(clickable)
            .heightIn(min = Dimens.minTouch)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(IconColumn), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f)) { content() }
    }
}

/** An icon for a [DetailRow]. */
@Composable
internal fun RowIcon(icon: ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(IconColumn)
    )
}
