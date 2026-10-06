// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.foundation.pager.PagerState

/** Goes to [page], with the slide unless the system removes animations ([reduceMotion]). */
internal suspend fun PagerState.moveTo(page: Int, reduceMotion: Boolean) {
    if (reduceMotion) scrollToPage(page) else animateScrollToPage(page)
}
