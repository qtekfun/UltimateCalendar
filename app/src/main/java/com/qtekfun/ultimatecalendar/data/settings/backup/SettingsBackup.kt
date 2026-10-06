// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindOption
import com.qtekfun.ultimatecalendar.domain.settings.FirstDayOfWeek
import com.qtekfun.ultimatecalendar.domain.settings.InitialView
import com.qtekfun.ultimatecalendar.domain.settings.InviteCheckInterval
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Layout of the file (the sealed envelope); a file with a higher one is from a newer app. */
const val BACKUP_FORMAT = 1

/** Version of what is inside; settings added later are optional, so older backups still open. */
const val BACKUP_CONTENT_VERSION = 2

private const val APP_ID = "UltimateCalendar"

/** The file: a small readable header and everything else sealed with the passphrase. */
@Serializable
data class BackupFile(val app: String = APP_ID, val format: Int = BACKUP_FORMAT, val sealed: Sealed)

/**
 * What is sealed: the settings, what only lives on this phone for each calendar (version 2; a
 * version 1 backup has none) and, in phase 6, the optional CalDAV session beside them.
 */
@Serializable
data class BackupContent(
    val version: Int = BACKUP_CONTENT_VERSION,
    val settings: BackupSettings,
    val calendars: List<BackupCalendar> = emptyList()
)

/**
 * The local name, color and visibility of one calendar (RF-02). Calendar ids belong to each
 * phone, so a calendar is found again by its account and the name its source gives it.
 */
@Serializable
data class BackupCalendar(
    val accountType: String,
    val accountName: String,
    val name: String,
    val displayName: String? = null,
    val color: Int? = null,
    val visible: Boolean? = null
)

/**
 * The settings as a backup keeps them: plain values, every one optional so a backup written by
 * an older app still opens and one written by a newer app is read as far as it is understood.
 * The default calendar is left out: calendar ids belong to the phone they were made on.
 */
@Serializable
data class BackupSettings(
    val theme: String? = null,
    val amoled: Boolean? = null,
    val dynamicColor: Boolean? = null,
    val firstDayOfWeek: String? = null,
    val initialView: String? = null,
    val defaultDurationMinutes: Int? = null,
    val defaultReminders: List<Int>? = null,
    val defaultAllDayReminders: List<Int>? = null,
    val inviteCheck: String? = null,
    val ownEmails: List<String>? = null,
    val notifyChanges: Boolean? = null,
    val notifyCancellations: Boolean? = null,
    val reRemind: String? = null,
    val missedWindowHours: Int? = null,
    val alarmClock: Boolean? = null,
    val robustMode: Boolean? = null,
    val allDayMinute: Int? = null
)

/** How a restore went. */
sealed interface RestoreResult {
    /** The settings are back; [calendars] are the calendar overrides still to be applied. */
    data class Restored(val calendars: List<BackupCalendar> = emptyList()) : RestoreResult

    /** The passphrase is wrong, or the file was altered: encryption cannot tell which. */
    data object WrongPassphrase : RestoreResult

    /** Not a backup of this app, or damaged beyond reading. */
    data object Invalid : RestoreResult

    /** Made by a newer version of the app, which this one cannot read. */
    data object NewerVersion : RestoreResult
}

/**
 * Exports and restores the app's settings (RF-11) to move to a new phone. The whole content is
 * encrypted with AES-GCM under a key derived from the user's passphrase. The default calendar
 * stays on its phone; the calendar overrides travel through [BackupCalendar] and are applied by
 * [CalendarOverridesBackup]; the CalDAV session joins the content in phase 6.
 */
class SettingsBackup @Inject constructor(private val settings: SettingsRepository) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * The backup file as text, with the local overrides of [calendars] beside the settings. The
     * passphrase must pass [SettingsRules.isPassphraseAcceptable].
     */
    fun export(passphrase: CharArray, calendars: List<BackupCalendar> = emptyList()): String {
        require(SettingsRules.isPassphraseAcceptable(passphrase)) { "Passphrase too short" }
        val content = BackupContent(settings = settings.current().toBackup(), calendars = calendars)
        val sealed = BackupCrypto.seal(
            json.encodeToString(content).toByteArray(),
            passphrase,
            aad(BACKUP_FORMAT)
        )
        return json.encodeToString(BackupFile(sealed = sealed))
    }

    fun restore(backup: String, passphrase: CharArray): RestoreResult {
        val file = parse<BackupFile>(backup)?.takeIf { it.app == APP_ID }
        return when {
            file == null -> RestoreResult.Invalid
            file.format > BACKUP_FORMAT -> RestoreResult.NewerVersion
            else -> restoreSealed(file, passphrase)
        }
    }

    private fun restoreSealed(file: BackupFile, passphrase: CharArray): RestoreResult {
        val plain = try {
            BackupCrypto.open(file.sealed, passphrase, aad(file.format))
        } catch (_: IllegalArgumentException) {
            return RestoreResult.Invalid
        }
        val content = plain?.let { parse<BackupContent>(it.decodeToString()) }
        return when {
            plain == null -> RestoreResult.WrongPassphrase

            content == null -> RestoreResult.Invalid

            content.version > BACKUP_CONTENT_VERSION -> RestoreResult.NewerVersion

            else -> {
                settings.update { content.settings.applyTo(it) }
                RestoreResult.Restored(content.calendars)
            }
        }
    }

    private inline fun <reified T> parse(text: String): T? = try {
        json.decodeFromString<T>(text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun aad(format: Int) = "$APP_ID:backup:$format".toByteArray()
}

private fun AppSettings.toBackup() = BackupSettings(
    theme = theme.name,
    amoled = amoled,
    dynamicColor = dynamicColor,
    firstDayOfWeek = firstDayOfWeek.name,
    initialView = initialView.name,
    defaultDurationMinutes = defaultDurationMinutes,
    defaultReminders = defaultReminders,
    defaultAllDayReminders = defaultAllDayReminders,
    inviteCheck = inviteCheck.name,
    ownEmails = ownEmails,
    notifyChanges = notifyChanges,
    notifyCancellations = notifyCancellations,
    reRemind = reRemind.name,
    missedWindowHours = missedWindowHours,
    alarmClock = alarmClock,
    robustMode = robustMode,
    allDayMinute = allDayMinute
)

/** [base] with what the backup has; whatever it lacks or does not understand stays as it was. */
private fun BackupSettings.applyTo(base: AppSettings) =
    applyLook(base).let(::applyCalendar).let(::applyReminders).let(::applyInvitations)

private fun BackupSettings.applyLook(base: AppSettings) = base.copy(
    theme = theme.asEnum<ThemeMode>() ?: base.theme,
    amoled = amoled ?: base.amoled,
    dynamicColor = dynamicColor ?: base.dynamicColor
)

private fun BackupSettings.applyCalendar(base: AppSettings) = base.copy(
    firstDayOfWeek = firstDayOfWeek.asEnum<FirstDayOfWeek>() ?: base.firstDayOfWeek,
    initialView = initialView.asEnum<InitialView>() ?: base.initialView,
    defaultDurationMinutes = defaultDurationMinutes ?: base.defaultDurationMinutes
)

private fun BackupSettings.applyReminders(base: AppSettings) = base.copy(
    defaultReminders = defaultReminders ?: base.defaultReminders,
    defaultAllDayReminders = defaultAllDayReminders ?: base.defaultAllDayReminders,
    missedWindowHours = missedWindowHours ?: base.missedWindowHours,
    alarmClock = alarmClock ?: base.alarmClock,
    robustMode = robustMode ?: base.robustMode,
    allDayMinute = allDayMinute ?: base.allDayMinute
)

private fun BackupSettings.applyInvitations(base: AppSettings) = base.copy(
    inviteCheck = inviteCheck.asEnum<InviteCheckInterval>() ?: base.inviteCheck,
    ownEmails = ownEmails ?: base.ownEmails,
    notifyChanges = notifyChanges ?: base.notifyChanges,
    notifyCancellations = notifyCancellations ?: base.notifyCancellations,
    reRemind = reRemind.asEnum<ReRemindOption>() ?: base.reRemind
)

private inline fun <reified E : Enum<E>> String?.asEnum(): E? =
    enumValues<E>().firstOrNull { it.name == this }
