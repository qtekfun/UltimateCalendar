// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavAccountState
import com.qtekfun.ultimatecalendar.domain.auth.LoginError
import com.qtekfun.ultimatecalendar.domain.auth.LoginState
import com.qtekfun.ultimatecalendar.domain.auth.LoginUiState
import com.qtekfun.ultimatecalendar.domain.caldav.CalDavCalendarItem
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.sync.engine.SyncStatus
import com.qtekfun.ultimatecalendar.ui.components.ComponentPreviews
import com.qtekfun.ultimatecalendar.ui.components.PreviewSurface
import java.time.Instant

// Invented data (example.com): nothing here is a real account.

private val previewAccount = SignedInAccount("https://cloud.example.com/", "ana")
private val previewSignedIn = CalDavAccountState.SignedIn(
    account = previewAccount,
    calendars = 3,
    addresses = listOf("ana@example.com"),
    scheduling = true,
    pending = 2,
    failed = 0
)
private fun item(id: Long, name: String, color: Int, writable: Boolean, enabled: Boolean) =
    CalDavCalendarItem(CalendarId(value = id), name, color, writable, enabled)

private val previewCalendars = listOf(
    item(id = 1, name = "Personal", color = 0xFF1E88E5.toInt(), writable = true, enabled = true),
    item(id = 2, name = "Work", color = 0xFF43A047.toInt(), writable = true, enabled = false),
    item(id = 3, name = "Holidays", color = 0xFFE53935.toInt(), writable = false, enabled = true)
)

@ComponentPreviews
@Composable
internal fun AccountPreview() = PreviewSurface {
    CalDavAccountContent(
        AccountUiState(
            previewSignedIn,
            previewCalendars,
            SyncStatus(SyncStatus.Phase.OK, Instant.parse("2026-10-06T08:00:00Z"))
        ),
        previewSignedIn,
        {},
        { _, _ -> },
        {}
    )
}

@ComponentPreviews
@Composable
internal fun AccountOfflinePreview() = PreviewSurface {
    CalDavAccountContent(
        AccountUiState(
            previewSignedIn,
            emptyList(),
            SyncStatus(SyncStatus.Phase.OFFLINE, Instant.parse("2026-10-05T08:00:00Z"))
        ),
        previewSignedIn,
        {},
        { _, _ -> },
        {}
    )
}

@ComponentPreviews
@Composable
internal fun AccountsRowPreview() = PreviewSurface {
    Column {
        AccountsRow(null) {}
        AccountsRow(previewSignedIn) {}
    }
}

@ComponentPreviews
@Composable
internal fun LoginFormPreview() = PreviewSurface {
    CalDavLoginContent(LoginUiState("cloud.example.com"), {}, {}, {}, {})
}

@ComponentPreviews
@Composable
internal fun LoginInsecurePreview() = PreviewSurface {
    CalDavLoginContent(LoginUiState("http://cloud.example.com"), {}, {}, {}, {})
}

@ComponentPreviews
@Composable
internal fun LoginWaitingPreview() = PreviewSurface {
    CalDavLoginContent(
        LoginUiState(
            "cloud.example.com",
            LoginState.WaitingForBrowser("https://cloud.example.com/login/v2/flow/abc")
        ),
        {},
        {},
        {},
        {}
    )
}

@ComponentPreviews
@Composable
internal fun LoginFailedPreview() = PreviewSurface {
    CalDavLoginContent(
        LoginUiState("cloud.example.com", LoginState.Failed(LoginError.TLS_ERROR)),
        {},
        {},
        {},
        {}
    )
}
