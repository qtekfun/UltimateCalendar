// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.adaptive

import androidx.compose.ui.tooling.preview.Preview

/**
 * The four windows every adaptive layout is previewed in: a phone, a 7" tablet (medium), a 10"
 * tablet in landscape (expanded) and a phone in landscape (medium, and short).
 */
@Preview(name = "Phone", showBackground = true, widthDp = 360, heightDp = 740)
@Preview(name = "Tablet 7in", showBackground = true, widthDp = 600, heightDp = 960)
@Preview(name = "Tablet 10in", showBackground = true, widthDp = 1280, heightDp = 800)
@Preview(name = "Phone landscape", showBackground = true, widthDp = 800, heightDp = 360)
annotation class AdaptivePreviews
