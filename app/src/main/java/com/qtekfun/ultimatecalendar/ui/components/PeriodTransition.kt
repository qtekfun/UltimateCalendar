// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.ui.theme.Motion
import com.qtekfun.ultimatecalendar.ui.theme.rememberReduceMotion
import java.time.LocalDate

/** What the calendar is showing: a view anchored on a date. */
data class PeriodKey(val view: CalendarView, val date: LocalDate)

/** How one period gives way to another. */
enum class PeriodMotion {
    /** A later period in the same view: slides in from the right. */
    FORWARD,

    /** An earlier period in the same view: slides in from the left. */
    BACKWARD,

    /** Another view: fades through, scaling up slightly. */
    SWITCH,

    /** Nothing changed. */
    NONE
}

/** The motion from [from] to [to]; pure, so the direction rules are tested. */
fun periodMotion(from: PeriodKey, to: PeriodKey): PeriodMotion = when {
    from.view != to.view -> PeriodMotion.SWITCH
    to.date.isAfter(from.date) -> PeriodMotion.FORWARD
    to.date.isBefore(from.date) -> PeriodMotion.BACKWARD
    else -> PeriodMotion.NONE
}

private const val SLIDE_DIVISOR = 5
private const val SWITCH_SCALE = 0.94f

/**
 * Animates [content] when [key] changes: periods slide in the direction of time, views fade
 * through. With the system's "remove animations" on, it only cross-fades (and Compose finishes
 * that at once). [content] must draw the period it is given, not the latest one, because both
 * the old and the new period are on screen during the transition.
 */
@Composable
fun AnimatedPeriod(
    key: PeriodKey,
    modifier: Modifier = Modifier,
    content: @Composable (PeriodKey) -> Unit
) {
    val reduce = rememberReduceMotion()
    AnimatedContent(
        targetState = key,
        modifier = modifier,
        transitionSpec = { transitionFor(periodMotion(initialState, targetState), reduce) },
        label = "period"
    ) { period -> content(period) }
}

private fun transitionFor(motion: PeriodMotion, reduce: Boolean): ContentTransform {
    val fastFade = tween<Float>(Motion.SHORT_MS)
    return when {
        reduce || motion == PeriodMotion.NONE -> fadeIn(fastFade) togetherWith fadeOut(fastFade)

        motion == PeriodMotion.SWITCH ->
            fadeIn(tween(Motion.MEDIUM_MS, Motion.SHORT_MS)) +
                scaleIn(tween(Motion.MEDIUM_MS, Motion.SHORT_MS), SWITCH_SCALE) togetherWith
                fadeOut(fastFade)

        else -> {
            val sign = if (motion == PeriodMotion.FORWARD) 1 else -1
            val slide = tween<IntOffset>(Motion.MEDIUM_MS, easing = Motion.EmphasizedDecelerate)
            val enter = slideInHorizontally(slide) { sign * it / SLIDE_DIVISOR } +
                fadeIn(tween(Motion.MEDIUM_MS))
            val exit =
                slideOutHorizontally(slide) { -sign * it / SLIDE_DIVISOR } + fadeOut(fastFade)
            enter togetherWith exit
        }
    }
}
