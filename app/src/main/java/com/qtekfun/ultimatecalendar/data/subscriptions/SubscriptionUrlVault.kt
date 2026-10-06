// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.data.auth.EncryptedSecret
import com.qtekfun.ultimatecalendar.data.auth.SecretCipher
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

/**
 * Keeps the addresses of subscriptions encrypted at rest, as the CalDAV login is (many feeds put
 * a secret token in the address): AES-GCM with the Keystore key, one text value per address.
 * The plain address only ever exists in memory and is never logged.
 */
class SubscriptionUrlVault @Inject constructor(private val cipher: SecretCipher) {
    fun seal(url: String): String {
        val secret = cipher.encrypt(url.toByteArray(Charsets.UTF_8))
        return encode(secret.iv) + SEPARATOR + encode(secret.ciphertext)
    }

    /** The address, or null when it cannot be decrypted (the Keystore key is gone) or is damaged. */
    fun open(sealed: String): String? {
        val parts = sealed.split(SEPARATOR)
        if (parts.size != PARTS) return null
        return try {
            val secret = EncryptedSecret(decode(parts[1]), decode(parts[0]))
            cipher.decrypt(secret).toString(Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Finds the same address again without decrypting every subscription: its SHA-256. */
    fun keyOf(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private companion object {
        const val SEPARATOR = ":"
        const val PARTS = 2
    }
}
