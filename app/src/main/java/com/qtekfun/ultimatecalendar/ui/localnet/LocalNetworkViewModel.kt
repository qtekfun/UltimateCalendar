// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.localnet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the system lets this app talk to the local network (always true before Android 17). */
fun interface LocalNetworkPermission {
    fun isGranted(): Boolean
}

/**
 * What to do once the system lets the app reach the local network again: sync at once, since the
 * syncs that failed while it was blocked are waiting out their retry delay.
 */
fun interface LocalNetworkGranted {
    suspend fun onGranted()
}

/** What the screens must show about the local network permission. */
data class LocalNetworkState(
    /** The server's host when it is on the local network and the system blocks it, else null. */
    val blockedHost: String? = null,
    /** The dialog that explains and asks is open. */
    val showDialog: Boolean = false,
    /** The user already answered the system prompt with "don't allow": only Settings can fix it. */
    val denied: Boolean = false
)

/**
 * Android 17 blocks connections to the local network unless the app holds `ACCESS_LOCAL_NETWORK`.
 * A server reached by a name that your DNS maps to `192.168.x.x` (split DNS) is one, and without
 * the permission every request just times out. This works out, from the server's host and the
 * permission, whether that is happening, so the app asks only then, and only for that reason.
 * Users whose server is public never see the question. It contacts nothing but the system
 * resolver.
 */
@HiltViewModel
class LocalNetworkViewModel @Inject constructor(
    private val permission: LocalNetworkPermission,
    private val resolver: HostResolver,
    private val onAccessGranted: LocalNetworkGranted
) : ViewModel() {
    private val mutable = MutableStateFlow(LocalNetworkState())
    val state: StateFlow<LocalNetworkState> = mutable.asStateFlow()
    private var checking: Job? = null

    /** Looks again: the screen was shown, the app came back from Settings, or the host changed. */
    fun check(host: String?) {
        checking?.cancel()
        if (host == null) {
            mutable.value = LocalNetworkState()
            return
        }
        if (permission.isGranted()) {
            unblock()
            return
        }
        checking = viewModelScope.launch {
            val local = isLocalHost(host)
            mutable.update { previous ->
                when {
                    !local -> LocalNetworkState()

                    // Newly blocked: explain at once; an earlier "not now" stays dismissed.
                    previous.blockedHost != host -> LocalNetworkState(host, showDialog = true)

                    else -> previous
                }
            }
        }
    }

    /** The user answered the system prompt: granted closes everything, denied leads to Settings. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) unblock() else mutable.update { it.copy(showDialog = true, denied = true) }
    }

    /** "Not now": the explanation closes until the app is started again. */
    fun dismiss() {
        mutable.update { it.copy(showDialog = false) }
    }

    /** Clears the block; if there was one, the app may reach its server now: sync at once. */
    private fun unblock() {
        val wasBlocked = mutable.value.blockedHost != null
        mutable.value = LocalNetworkState()
        if (wasBlocked) viewModelScope.launch { onAccessGranted.onGranted() }
    }

    private suspend fun isLocalHost(host: String): Boolean {
        if (host.endsWith(".local", ignoreCase = true)) return true
        return resolver.resolve(host).any(LocalNetwork::isLocal)
    }
}
