// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.CredentialsProvider
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalDavProviderTest {
    @StartStop
    val server = MockWebServer()

    private fun provider(credentials: Credentials?) = CalDavProvider(
        OkHttpClient(),
        CredentialsProvider { credentials },
        Dispatchers.Unconfined
    )

    private fun account() = SignedInAccount(server.url("/nextcloud/").toString(), "ana")

    @Test
    fun `refuses an account whose address is not https`() {
        assertNull(provider(Credentials("ana", "pw")).connect(account()))
    }

    @Test
    fun `refuses an address that is not valid`() {
        assertNull(provider(null).connect(SignedInAccount("https://", "ana"), allowInsecure = true))
    }

    @Test
    fun `every request carries the app password as basic auth`() = runTest {
        val dav = provider(Credentials("ana", "app pässword"))
            .connect(account(), allowInsecure = true)
        assertNotNull(dav)
        server.enqueue(MockResponse(404))

        dav!!.read.discover()

        val basic = server.takeRequest().headers["Authorization"].orEmpty().removePrefix("Basic ")
        assertEquals("ana:app pässword", String(Base64.getDecoder().decode(basic)))
    }

    @Test
    fun `without credentials no authorization is sent`() = runTest {
        val dav = provider(null).connect(account(), allowInsecure = true)!!
        server.enqueue(MockResponse(401))

        assertEquals(DavResult.Unauthorized, dav.read.discover())

        assertTrue(server.takeRequest().headers["Authorization"] == null)
    }

    @Test
    fun `reads and writes go to the same server path`() = runTest {
        val dav = provider(Credentials("ana", "pw")).connect(account(), allowInsecure = true)!!
        server.enqueue(MockResponse(201))

        dav.write.put("/nextcloud/remote.php/dav/calendars/ana/x/e.ics", "BEGIN:VCALENDAR", null)

        assertEquals(
            "/nextcloud/remote.php/dav/calendars/ana/x/e.ics",
            server.takeRequest().target
        )
    }
}
