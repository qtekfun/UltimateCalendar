// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.sync.ReplyUpload
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import com.qtekfun.ultimatecalendar.ui.theme.Spacing

/**
 * Under the answer buttons, while the user's answer is stored on the phone but has not reached
 * the organizer: says so plainly and, when the account can sync, offers to send it now. It goes
 * away by itself once the account has uploaded the answer.
 */
@Composable
internal fun ReplyUploadLine(
    upload: ReplyUpload,
    onSendNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = Spacing.l)) {
        Text(
            stringResource(
                when (upload) {
                    ReplyUpload.SENDING -> R.string.detail_reply_sending
                    ReplyUpload.WAITING -> R.string.detail_reply_waiting
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (upload == ReplyUpload.SENDING) {
            TextButton(onClick = onSendNow, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                Text(stringResource(R.string.detail_reply_send_now))
            }
        }
    }
}
