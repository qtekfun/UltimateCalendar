// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/**
 * Nothing to show yet: an [illustration], a [title], a helpful [body] and, when there is
 * something to do about it, one action. Centered and scrollable-friendly (it wraps content).
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = { EmptyCalendarIllustration() },
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically)
    ) {
        illustration()
        // One node, announced politely when it appears (an empty search, a view without events).
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Text(
                title,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null) {
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private const val SUN = "M138,19a15,15 0 1,0 0.01,0z"
private const val PAGE =
    "M34,34h100a14,14 0 0 1 14,14v86a14,14 0 0 1 -14,14h-100a14,14 0 0 1 -14,-14v-86" +
        "a14,14 0 0 1 14,-14z"
private const val BAND = "M34,34h100a14,14 0 0 1 14,14v20h-128v-20a14,14 0 0 1 14,-14z"
private const val RINGS = "M54,16v34 M114,16v34"
private const val DOTS =
    "M45,88h0 M71,88h0 M97,88h0 M123,88h0 M45,108h0 M71,108h0 M97,108h0 M123,108h0 " +
        "M45,128h0 M71,128h0 M97,128h0 M123,128h0"
private const val DOT_STROKE = 9f
private const val TODAY_RING = "M107,108a10,10 0 1,0 -20,0a10,10 0 1,0 20,0z"
private const val VIEWPORT = 168f
private const val RING_STROKE = 8f
private const val TODAY_STROKE = 3f

private fun illustration(scheme: ColorScheme): ImageVector {
    val sun = scheme.tertiaryContainer
    val page = scheme.surfaceContainerHighest
    val band = scheme.primary
    val ring = scheme.outline
    val dot = scheme.outlineVariant
    val today = scheme.primary
    fun ImageVector.Builder.shape(data: String, fill: Color) =
        addPath(PathParser().parsePathString(data).toNodes(), fill = SolidColor(fill))

    val size = Dimens.illustration
    return ImageVector.Builder("EmptyCalendar", size, size, VIEWPORT, VIEWPORT)
        .shape(SUN, sun)
        .shape(PAGE, page)
        .shape(BAND, band)
        .addPath(
            PathParser().parsePathString(RINGS).toNodes(),
            stroke = SolidColor(ring),
            strokeLineWidth = RING_STROKE,
            strokeLineCap = StrokeCap.Round
        )
        .addPath(
            PathParser().parsePathString(DOTS).toNodes(),
            stroke = SolidColor(dot),
            strokeLineWidth = DOT_STROKE,
            strokeLineCap = StrokeCap.Round
        )
        .addPath(
            PathParser().parsePathString(TODAY_RING).toNodes(),
            stroke = SolidColor(today),
            strokeLineWidth = TODAY_STROKE
        )
        .build()
}

/**
 * A calendar page with a sun peeking out, drawn as a vector from theme colors so it follows
 * light, dark and wallpaper colors; no bitmap asset. Decorative (hidden from screen readers).
 */
@Composable
fun EmptyCalendarIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val image = remember(scheme) { illustration(scheme) }
    Image(image, contentDescription = null, modifier = modifier.size(Dimens.illustration))
}

@ComponentPreviews
@Composable
internal fun EmptyStatePreview() {
    PreviewSurface {
        EmptyState(
            title = stringResource(R.string.cal_empty_title),
            body = stringResource(R.string.cal_empty_body),
            actionLabel = stringResource(R.string.shell_create),
            onAction = {}
        )
    }
}
