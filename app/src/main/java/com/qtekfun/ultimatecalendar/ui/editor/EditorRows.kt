// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/** Width of the icon column, so every field lines up whether it has an icon or not. */
private val IconColumn = 24.dp

/**
 * One line of the editor: an optional leading [icon] (decorative: the text says the rest),
 * or a [leading] composable in its place, then the [content]. With [onClick] the whole row is
 * one 48 dp target described by [description]; it grows with the font size rather than
 * clipping.
 */
@Composable
internal fun EditorRow(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    description: String? = null,
    role: Role = Role.Button,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(role = role, onClick = onClick).semantics(mergeDescendants = true) {
            description?.let { contentDescription = it }
        }
    } else {
        Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(clickable)
            .heightIn(min = Dimens.minTouch)
            .padding(horizontal = Spacing.l, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(Modifier.size(IconColumn), contentAlignment = Alignment.Center) { leading() }
        } else if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(IconColumn),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.width(IconColumn))
        }
        Spacer(Modifier.width(Spacing.l))
        content()
    }
}

/** The text of a picker row: wraps rather than truncating, since dates matter. */
@Composable
internal fun RowScope.RowText(
    text: String,
    modifier: Modifier = Modifier,
    secondary: Boolean = false
) {
    Text(
        text,
        modifier = modifier.weight(1f),
        style = MaterialTheme.typography.bodyLarge,
        color = if (secondary) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        overflow = TextOverflow.Ellipsis,
        maxLines = MAX_ROW_LINES
    )
}

private const val MAX_ROW_LINES = 4

/** A short problem under a field, in the error color. */
@Composable
internal fun ProblemText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = Spacing.xxl + Spacing.xl, end = Spacing.l),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
