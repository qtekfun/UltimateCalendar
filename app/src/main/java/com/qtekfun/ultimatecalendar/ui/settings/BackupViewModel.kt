// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.settings.backup.RestoreResult
import com.qtekfun.ultimatecalendar.data.settings.backup.SettingsBackup
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Exports the settings to a file the user picks, and restores them from one (RF-11). */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: SettingsBackup,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {
    private val mutableMessages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private val pending = MutableStateFlow<String?>(null)

    /** Messages to show, as string resources. */
    val messages: SharedFlow<Int> = mutableMessages.asSharedFlow()

    /** A backup read from a file, waiting for its passphrase. */
    val needsPassphrase: StateFlow<String?> = pending.asStateFlow()

    fun export(target: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            val written = withContext(io) {
                val text = backup.export(passphrase)
                runCatchingIo {
                    context.contentResolver.openOutputStream(target)?.use {
                        it.write(text.toByteArray())
                    }
                }
            }
            mutableMessages.tryEmit(
                if (written != null) R.string.backup_exported else R.string.backup_failed
            )
        }
    }

    fun startRestore(source: Uri) {
        viewModelScope.launch {
            val text = withContext(io) {
                runCatchingIo {
                    context.contentResolver.openInputStream(source)?.use {
                        it.readBytes().decodeToString()
                    }
                }
            }
            if (text ==
                null
            ) {
                mutableMessages.tryEmit(R.string.backup_invalid)
            } else {
                pending.value = text
            }
        }
    }

    fun finishRestore(passphrase: CharArray) {
        val text = pending.value ?: return
        viewModelScope.launch {
            val result = withContext(io) { backup.restore(text, passphrase) }
            // A wrong passphrase leaves the dialog open to try again; anything else ends it.
            if (result != RestoreResult.WrongPassphrase) pending.value = null
            mutableMessages.tryEmit(
                when (result) {
                    RestoreResult.Restored -> R.string.backup_restored
                    RestoreResult.WrongPassphrase -> R.string.backup_wrong_passphrase
                    RestoreResult.Invalid -> R.string.backup_invalid
                    RestoreResult.NewerVersion -> R.string.backup_newer_version
                }
            )
        }
    }

    fun cancelRestore() {
        pending.value = null
    }

    private inline fun <T> runCatchingIo(block: () -> T?): T? = try {
        block()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
