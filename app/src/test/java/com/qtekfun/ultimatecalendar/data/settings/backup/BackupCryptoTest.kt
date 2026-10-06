// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import java.util.Base64
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BackupCryptoTest {
    private val secret = "correct horse".toCharArray()
    private val aad = "header".toByteArray()
    private val plain = "hello, calendar".toByteArray()

    @Test
    fun `what is sealed opens with the same passphrase`() {
        val sealed = BackupCrypto.seal(plain, secret, aad)
        assertEquals(BackupCrypto.ITERATIONS, sealed.iterations)
        assertArrayEquals(plain, BackupCrypto.open(sealed, secret, aad))
    }

    @Test
    fun `sealing twice never repeats the salt or the iv`() {
        val first = BackupCrypto.seal(plain, secret, aad)
        val second = BackupCrypto.seal(plain, secret, aad)
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.iv, second.iv)
        assertNotEquals(first.data, second.data)
    }

    @Test
    fun `the plain text is not in the sealed data`() {
        val sealed = BackupCrypto.seal(plain, secret, aad)
        assertNotEquals(String(plain), String(Base64.getDecoder().decode(sealed.data)))
    }

    @Test
    fun `a wrong passphrase opens nothing`() {
        val sealed = BackupCrypto.seal(plain, secret, aad)
        assertNull(BackupCrypto.open(sealed, "correct horsf".toCharArray(), aad))
    }

    @Test
    fun `altered data opens nothing`() {
        val sealed = BackupCrypto.seal(plain, secret, aad)
        val bytes = Base64.getDecoder().decode(sealed.data)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        val altered = sealed.copy(data = Base64.getEncoder().encodeToString(bytes))
        assertNull(BackupCrypto.open(altered, secret, aad))
    }

    @Test
    fun `an altered header opens nothing`() {
        val sealed = BackupCrypto.seal(plain, secret, aad)
        assertNull(BackupCrypto.open(sealed, secret, "other".toByteArray()))
    }

    @Test
    fun `data shorter than the authentication tag opens nothing`() {
        val sealed = BackupCrypto.seal(plain, secret, aad)
        val short = sealed.copy(data = Base64.getEncoder().encodeToString(ByteArray(3)))
        assertNull(BackupCrypto.open(short, secret, aad))
    }

    @Test
    fun `key stretching outside the accepted range is refused`() {
        val sealed = BackupCrypto.seal(plain, secret, aad)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.open(sealed.copy(iterations = 1), secret, aad)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.open(sealed.copy(iterations = Int.MAX_VALUE), secret, aad)
        }
    }

    @Test
    fun `a salt or iv of the wrong size is refused`() {
        val sealed = BackupCrypto.seal(plain, secret, aad)
        val short = Base64.getEncoder().encodeToString(ByteArray(4))
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.open(sealed.copy(salt = short), secret, aad)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.open(sealed.copy(iv = short), secret, aad)
        }
    }

    @Test
    fun `data that is not base64 is refused`() {
        val sealed = BackupCrypto.seal(plain, secret, aad)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.open(sealed.copy(data = "***"), secret, aad)
        }
    }
}
