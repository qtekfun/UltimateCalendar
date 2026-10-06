// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The Accept, Maybe and Decline buttons of an invitation notification (RF-07). */
@AndroidEntryPoint
class InvitationActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var handler: InvitationActionHandler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != InvitationIntents.ACTION_ANSWER) return
        val (key, answer) = InvitationIntents.answerOf(intent) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                handler.answer(key, answer)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
