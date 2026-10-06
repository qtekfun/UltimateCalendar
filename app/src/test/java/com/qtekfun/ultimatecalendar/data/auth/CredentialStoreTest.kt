// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

import com.qtekfun.ultimatecalendar.data.remote.Credentials
import java.security.GeneralSecurityException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CredentialStoreTest {
    private val storage = FakeLoginStorage()
    private val store = CredentialStore(storage, FakeCipher())

    @Test
    fun `saves and loads the server and credentials`() {
        store.save("https://cloud.example.com/", Credentials("ana", "app-pässwörd-123"))

        val loaded = store.load()!!
        assertEquals("https://cloud.example.com/", loaded.serverUrl)
        assertEquals("ana", loaded.credentials.loginName)
        assertEquals("app-pässwörd-123", loaded.credentials.appPassword)
    }

    @Test
    fun `stores the app password only encrypted`() {
        val password = "plain-app-password"

        store.save("https://cloud.example.com/", Credentials("ana", password))

        val stored = storage.read()!!
        assertFalse(stored.secret.ciphertext.toString(Charsets.UTF_8).contains(password))
        assertFalse(storage.rawText().contains(password))
        assertEquals(12, stored.secret.iv.size)
    }

    @Test
    fun `saving again replaces the credentials`() {
        store.save("https://cloud.example.com/", Credentials("ana", "old"))

        store.save("https://cloud.example.com/", Credentials("ana", "new"))

        assertEquals("new", store.load()?.credentials?.appPassword)
    }

    @Test
    fun `nothing is loaded before signing in or after clearing`() {
        assertNull(store.load())

        store.save("https://cloud.example.com/", Credentials("ana", "pw"))
        store.clear()

        assertNull(store.load())
        assertNull(storage.read())
    }

    @Test
    fun `a login the Keystore can no longer decrypt is dropped`() {
        val lostKey = object : SecretCipher {
            override fun encrypt(plaintext: ByteArray) = EncryptedSecret(plaintext, ByteArray(12))

            override fun decrypt(secret: EncryptedSecret): ByteArray =
                throw GeneralSecurityException("key permanently invalidated")
        }
        val broken = CredentialStore(storage, lostKey)
        broken.save("https://cloud.example.com/", Credentials("ana", "pw"))

        assertNull(broken.load())
        assertNull(storage.read())
    }
}
