// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.localnet

import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalNetworkViewModelTest {
    private var granted = false
    private val answers = mutableMapOf<String, List<String>>()
    private val asked = mutableListOf<String>()
    private val resolver = HostResolver { host ->
        asked += host
        (answers[host] ?: emptyList()).map { InetAddress.getByName(it) }
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = LocalNetworkViewModel({ granted }, resolver)

    @Test
    fun `no server, nothing to ask`() {
        val vm = viewModel()
        vm.check(null)
        assertEquals(LocalNetworkState(), vm.state.value)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a granted permission never resolves anything`() {
        granted = true
        val vm = viewModel()
        vm.check("cloud.example.com")
        assertEquals(LocalNetworkState(), vm.state.value)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a public server needs nothing`() {
        answers["cloud.example.com"] = listOf("203.0.113.7")
        val vm = viewModel()
        vm.check("cloud.example.com")
        assertEquals(LocalNetworkState(), vm.state.value)
    }

    @Test
    fun `a name that resolves to a LAN address is blocked and explained at once`() {
        answers["cloud.example.com"] = listOf("2606:4700::1", "192.168.1.6")
        val vm = viewModel()
        vm.check("cloud.example.com")
        assertEquals(
            LocalNetworkState(blockedHost = "cloud.example.com", showDialog = true),
            vm.state.value
        )
    }

    @Test
    fun `a literal LAN address and a dot-local name are blocked, the latter without a lookup`() {
        answers["192.168.1.6"] = listOf("192.168.1.6")
        val literal = viewModel()
        literal.check("192.168.1.6")
        assertEquals("192.168.1.6", literal.state.value.blockedHost)

        val mdns = viewModel()
        mdns.check("cloud.local")
        assertEquals("cloud.local", mdns.state.value.blockedHost)
        assertFalse("cloud.local" in asked)
    }

    @Test
    fun `granting closes it, refusing leads to Settings`() {
        answers["cloud.example.com"] = listOf("192.168.1.6")
        val vm = viewModel()
        vm.check("cloud.example.com")

        vm.onPermissionResult(granted = false)
        assertEquals(
            LocalNetworkState("cloud.example.com", showDialog = true, denied = true),
            vm.state.value
        )

        vm.onPermissionResult(granted = true)
        assertEquals(LocalNetworkState(), vm.state.value)
    }

    @Test
    fun `not now closes the dialog and it stays closed for the same host`() {
        answers["cloud.example.com"] = listOf("192.168.1.6")
        answers["other.example.com"] = listOf("10.0.0.2")
        val vm = viewModel()
        vm.check("cloud.example.com")

        vm.dismiss()
        assertEquals(LocalNetworkState("cloud.example.com"), vm.state.value)

        vm.check("cloud.example.com")
        assertFalse(vm.state.value.showDialog)

        // Another server is a new question.
        vm.check("other.example.com")
        assertEquals(
            LocalNetworkState("other.example.com", showDialog = true),
            vm.state.value
        )
    }

    @Test
    fun `allowing it in Settings and coming back clears the block`() {
        answers["cloud.example.com"] = listOf("192.168.1.6")
        val vm = viewModel()
        vm.check("cloud.example.com")
        assertEquals("cloud.example.com", vm.state.value.blockedHost)

        granted = true
        vm.check("cloud.example.com")
        assertEquals(LocalNetworkState(), vm.state.value)
    }

    @Test
    fun `the server resolving to a public address again lifts the block`() {
        answers["cloud.example.com"] = listOf("192.168.1.6")
        val vm = viewModel()
        vm.check("cloud.example.com")

        answers["cloud.example.com"] = listOf("203.0.113.7")
        vm.check("cloud.example.com")
        assertEquals(LocalNetworkState(), vm.state.value)
    }

    @Test
    fun `signing out clears the block`() {
        answers["cloud.example.com"] = listOf("192.168.1.6")
        val vm = viewModel()
        vm.check("cloud.example.com")

        vm.check(null)
        assertEquals(LocalNetworkState(), vm.state.value)
    }
}
