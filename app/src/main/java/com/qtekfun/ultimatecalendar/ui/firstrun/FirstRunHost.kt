// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.firstrun

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.qtekfun.ultimatecalendar.domain.firstrun.FirstRunFlag

/** Opens the wizard from any screen, for instance from Settings (RF-01). */
val LocalOpenWizard = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Shows the wizard the first time the app opens, in place of [content], and lets any screen
 * under it open it again through [LocalOpenWizard]. Closing it, by "Done" or going back, counts
 * as seen: it is not shown by itself again. [onConnectCalDav] is the wizard's way to the CalDAV
 * connection (RF-12): it closes the wizard, as seen, and asks the app to open that screen.
 */
@Composable
fun FirstRunHost(
    flag: FirstRunFlag,
    onConnectCalDav: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var open by rememberSaveable { mutableStateOf(!flag.isDone()) }
    val close = {
        flag.markDone()
        open = false
    }
    CompositionLocalProvider(LocalOpenWizard provides { open = true }) {
        if (open) {
            BackHandler(onBack = close)
            ReliabilityWizardScreen(
                onDone = close,
                onConnectCalDav = {
                    close()
                    onConnectCalDav()
                }
            )
        } else {
            content()
        }
    }
}
