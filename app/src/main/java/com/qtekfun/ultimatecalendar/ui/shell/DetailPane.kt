// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.compose.runtime.Composable
import com.qtekfun.ultimatecalendar.domain.detail.EventRef

/**
 * The event the Agenda's second pane shows on a wide window and how to draw it. The owner of
 * the navigation state holds [selected], so it survives rotation and resizing; the shell only
 * lays the pane out.
 */
class DetailPane(val selected: EventRef?, val content: @Composable (EventRef) -> Unit)
