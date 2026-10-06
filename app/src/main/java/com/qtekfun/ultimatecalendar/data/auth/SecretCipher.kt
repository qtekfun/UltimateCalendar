// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

/** Ciphertext and the IV it was encrypted with. */
class EncryptedSecret(val ciphertext: ByteArray, val iv: ByteArray)

/** Encrypts secrets at rest. The production implementation keeps its key in Android Keystore. */
interface SecretCipher {
    fun encrypt(plaintext: ByteArray): EncryptedSecret

    fun decrypt(secret: EncryptedSecret): ByteArray
}
