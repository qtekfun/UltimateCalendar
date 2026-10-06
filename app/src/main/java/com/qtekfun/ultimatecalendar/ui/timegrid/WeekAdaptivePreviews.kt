// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.runtime.Composable
import com.qtekfun.ultimatecalendar.ui.adaptive.AdaptivePreviews
import com.qtekfun.ultimatecalendar.ui.adaptive.WithAdaptiveGridScale

/** Week on a phone, a 7" and a 10" tablet and a phone in landscape: bigger hours and text. */
@AdaptivePreviews
@Composable
internal fun WeekAdaptivePreview() = WithAdaptiveGridScale { WeekPreview() }
