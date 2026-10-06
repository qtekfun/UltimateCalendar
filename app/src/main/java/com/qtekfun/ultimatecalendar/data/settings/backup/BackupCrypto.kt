// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable

private const val KEY_BITS = 256
private const val SALT_BYTES = 16
private const val IV_BYTES = 12
private const val TAG_BITS = 128

/** Data sealed with a passphrase: everything but the passphrase is needed to open it again. */
@Serializable
data class Sealed(val iterations: Int, val salt: String, val iv: String, val data: String)

/**
 * Passphrase-based encryption for a backup (RF-11): AES-256-GCM with a key derived by
 * PBKDF2-HMAC-SHA256, as UltimateTasks does. A Keystore key cannot leave the phone, so a backup
 * that moves to a new phone is protected by a passphrase the user chooses instead. The
 * [aad] (the file's own header) is authenticated too, so altering it breaks the file.
 */
object BackupCrypto {
    const val ITERATIONS = 210_000
    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 2_000_000

    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun seal(plain: ByteArray, passphrase: CharArray, aad: ByteArray): Sealed {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key(passphrase, salt, ITERATIONS),
            GCMParameterSpec(TAG_BITS, iv)
        )
        cipher.updateAAD(aad)
        return Sealed(
            ITERATIONS,
            encoder.encodeToString(salt),
            encoder.encodeToString(iv),
            encoder.encodeToString(cipher.doFinal(plain))
        )
    }

    /**
     * The original data, or null if the passphrase is wrong or the data was altered (GCM cannot
     * tell the two apart).
     *
     * @throws IllegalArgumentException if [sealed] is not well formed.
     */
    fun open(sealed: Sealed, passphrase: CharArray, aad: ByteArray): ByteArray? {
        require(sealed.iterations in MIN_ITERATIONS..MAX_ITERATIONS) {
            "Unsupported key stretching"
        }
        val salt = decoder.decode(sealed.salt)
        val iv = decoder.decode(sealed.iv)
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES) { "Unsupported salt or IV" }
        val data = decoder.decode(sealed.data)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(passphrase, salt, sealed.iterations),
                GCMParameterSpec(TAG_BITS, iv)
            )
            cipher.updateAAD(aad)
            cipher.doFinal(data)
        } catch (_: AEADBadTagException) {
            null
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance(
            "PBKDF2WithHmacSHA256"
        ).generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
