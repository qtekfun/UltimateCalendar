// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.data.auth.EncryptedSecret
import com.qtekfun.ultimatecalendar.data.auth.FakeCipher
import com.qtekfun.ultimatecalendar.data.auth.SecretCipher
import java.security.GeneralSecurityException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionUrlVaultTest {
    private val vault = SubscriptionUrlVault(FakeCipher())
    private val url = "https://cal.example.com/private/abc123/basic.ics?token=s3cr3t"

    @Test
    fun `an address comes back as it went in and the sealed text hides it`() {
        val sealed = vault.seal(url)

        assertEquals(url, vault.open(sealed))
        assertFalse("s3cr3t" in sealed)
        assertFalse("cal.example.com" in sealed)
    }

    @Test
    fun `sealing twice gives different texts`() {
        assertNotEquals(vault.seal(url), vault.seal(url))
    }

    @Test
    fun `damaged text opens as nothing`() {
        listOf("", "nonsense", "a:b:c", "!!!:???", vault.seal(url).substringBefore(':')).forEach {
            assertNull(vault.open(it), it)
        }
    }

    @Test
    fun `a key that is gone opens as nothing`() {
        val lost = object : SecretCipher {
            override fun encrypt(plaintext: ByteArray) = EncryptedSecret(plaintext, ByteArray(12))

            override fun decrypt(secret: EncryptedSecret): ByteArray =
                throw GeneralSecurityException("key permanently invalidated")
        }

        assertNull(SubscriptionUrlVault(lost).open(SubscriptionUrlVault(lost).seal(url)))
    }

    @Test
    fun `the same address has the same key, a different one another`() {
        assertEquals(vault.keyOf(url), vault.keyOf(url))
        assertNotEquals(vault.keyOf(url), vault.keyOf("$url&x=1"))
        assertEquals(64, vault.keyOf(url).length)
        assertFalse("s3cr3t" in vault.keyOf(url))
    }
}
