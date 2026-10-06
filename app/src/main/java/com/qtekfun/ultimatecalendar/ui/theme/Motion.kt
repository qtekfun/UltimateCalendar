// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Durations and easing of the app's motion (Material 3 "emphasized"). */
object Motion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 300
    const val LONG_MS = 400

    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** The system "remove animations" switch sets the animator scale to 0. */
    fun isReduced(animatorScale: Float): Boolean = animatorScale <= 0f
}

/**
 * True when the user turned animations off in the system. Compose's own animations already
 * finish at once then; this lets a screen also pick a calmer transition (no slide, no shimmer).
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Motion.isReduced(scale)
    }
}
