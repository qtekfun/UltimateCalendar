// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.ui.theme.CalendarShapes
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import com.qtekfun.ultimatecalendar.ui.theme.rememberReduceMotion

private const val PULSE_MS = 900
private const val PULSE_LOW = 0.45f
private const val PULSE_HIGH = 1f

/** A gentle pulse for placeholders; still (full strength) when the system removes animations. */
@Composable
fun Modifier.skeletonPulse(): Modifier {
    if (rememberReduceMotion()) return this
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = PULSE_LOW,
        targetValue = PULSE_HIGH,
        animationSpec = infiniteRepeatable(
            tween(PULSE_MS, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    return alpha(alpha)
}

/** One grey block standing in for text or a chip while data loads. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .clip(CalendarShapes.eventChip)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

/** A loading agenda: day badge circles with chip-shaped rows. Announces "Loading". */
@Composable
fun EventListSkeleton(modifier: Modifier = Modifier, days: Int = 3) {
    val loading = stringResource(R.string.cal_loading)
    Column(
        modifier
            .skeletonPulse()
            .semantics { contentDescription = loading }
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l)
    ) {
        repeat(days) { day ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Spacer(
                    Modifier
                        .size(Dimens.dayBadge)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    horizontalAlignment = Alignment.Start
                ) {
                    repeat(day % 2 + 1) {
                        SkeletonBlock(Modifier.fillMaxWidth().height(Dimens.minTouch))
                    }
                    SkeletonBlock(Modifier.width(Dimens.illustration).height(Spacing.l))
                }
            }
        }
    }
}

@ComponentPreviews
@Composable
internal fun SkeletonPreview() {
    PreviewSurface { EventListSkeleton() }
}
