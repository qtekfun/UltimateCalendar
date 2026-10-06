// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.settings.backup.BackupCoordinator
import com.qtekfun.ultimatecalendar.data.settings.backup.RestoreResult
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
    private val backup: BackupCoordinator,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {
    private val mutableMessages = MutableSharedFlow<BackupMessage>(extraBufferCapacity = MESSAGES)
    private val pending = MutableStateFlow<String?>(null)

    /** Messages to show. */
    val messages: SharedFlow<BackupMessage> = mutableMessages.asSharedFlow()

    /** A backup read from a file, waiting for its passphrase. */
    val needsPassphrase: StateFlow<String?> = pending.asStateFlow()

    /** [includeSession] puts the CalDAV sign-in in the file, inside the encrypted content. */
    fun export(target: Uri, passphrase: CharArray, includeSession: Boolean) {
        viewModelScope.launch {
            val text = backup.export(passphrase, includeSession)
            val written = withContext(io) {
                runCatchingIo {
                    context.contentResolver.openOutputStream(target)?.use {
                        it.write(text.toByteArray())
                    }
                }
            }
            mutableMessages.tryEmit(
                BackupMessage.Text(
                    if (written != null) R.string.backup_exported else R.string.backup_failed
                )
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
            if (text == null) {
                mutableMessages.tryEmit(BackupMessage.Text(R.string.backup_invalid))
            } else {
                pending.value = text
            }
        }
    }

    fun finishRestore(passphrase: CharArray) {
        val text = pending.value ?: return
        viewModelScope.launch {
            val outcome = backup.restore(text, passphrase)
            // A wrong passphrase leaves the dialog open to try again; anything else ends it.
            if (outcome.result != RestoreResult.WrongPassphrase) pending.value = null
            restoreMessages(outcome).forEach { mutableMessages.tryEmit(it) }
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

    private companion object {
        const val MESSAGES = 6
    }
}
