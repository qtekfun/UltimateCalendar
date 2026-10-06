// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The shell's snackbar host, for the views inside it to tell the user something (a change they
 * can undo, why something cannot be done). Null outside the shell, where a view has no host.
 */
val LocalSnackbarHost = staticCompositionLocalOf<SnackbarHostState?> { null }
