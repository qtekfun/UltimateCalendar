// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

import com.qtekfun.ultimatecalendar.data.settings.FakePreferences
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PreferencesLoginStorageTest {
    private val preferences = FakePreferences()
    private val storage = PreferencesLoginStorage(preferences)
    private val login = StoredLogin(
        "https://cloud.example.com/",
        "ana",
        EncryptedSecret(byteArrayOf(1, 2, 3, -128, 127), ByteArray(12) { it.toByte() })
    )

    @Test
    fun `a login survives being written and read`() {
        storage.write(login)

        val read = storage.read()!!
        assertEquals(login.serverUrl, read.serverUrl)
        assertEquals(login.loginName, read.loginName)
        assertArrayEquals(login.secret.ciphertext, read.secret.ciphertext)
        assertArrayEquals(login.secret.iv, read.secret.iv)
    }

    @Test
    fun `only text that is not the password is stored`() {
        storage.write(login)

        assertFalse(preferences.values.values.any { it.toString().contains("password") })
    }

    @Test
    fun `nothing is read from an empty file`() {
        assertNull(storage.read())
    }

    @Test
    fun `an incomplete or damaged entry reads as no login`() {
        storage.write(login)
        preferences.edit().remove("iv").apply()
        assertNull(storage.read())

        storage.write(login)
        preferences.edit().putString("ciphertext", "not base64 !!").apply()
        assertNull(storage.read())
    }

    @Test
    fun `clearing deletes the login`() {
        storage.write(login)

        storage.clear()

        assertNull(storage.read())
        assertEquals(emptyMap<String, Any?>(), preferences.values)
    }
}
