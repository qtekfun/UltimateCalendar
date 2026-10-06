// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EdgeScrollTest {
    @Test
    fun `nothing scrolls away from the edge`() {
        assertEquals(0f, EdgeScroll.speed(distance = 80f, zone = 80f, maxSpeed = 1000f))
        assertEquals(0f, EdgeScroll.speed(distance = 300f, zone = 80f, maxSpeed = 1000f))
    }

    @Test
    fun `the closer to the edge, the faster`() {
        assertEquals(500f, EdgeScroll.speed(distance = 40f, zone = 80f, maxSpeed = 1000f))
        assertEquals(1000f, EdgeScroll.speed(distance = 0f, zone = 80f, maxSpeed = 1000f))
        assertEquals(1000f, EdgeScroll.speed(distance = -50f, zone = 80f, maxSpeed = 1000f))
    }

    @Test
    fun `the zone needs some size`() {
        assertThrows<IllegalArgumentException> { EdgeScroll.speed(10f, 0f, 100f) }
    }

    @Test
    fun `a finger near the top scrolls up and near the bottom scrolls down`() {
        assertEquals(
            -500f,
            EdgeScroll.vertical(140f, top = 100f, bottom = 600f, zone = 80f, maxSpeed = 1000f)
        )
        assertEquals(
            500f,
            EdgeScroll.vertical(560f, top = 100f, bottom = 600f, zone = 80f, maxSpeed = 1000f)
        )
        assertEquals(
            0f,
            EdgeScroll.vertical(350f, top = 100f, bottom = 600f, zone = 80f, maxSpeed = 1000f)
        )
        assertEquals(
            -1000f,
            EdgeScroll.vertical(20f, top = 100f, bottom = 600f, zone = 80f, maxSpeed = 1000f)
        )
    }

    @Test
    fun `the sides of the grid push towards the neighbouring page`() {
        assertEquals(PageEdge.PREVIOUS, EdgeScroll.horizontal(10f, 0f, 400f, 32f))
        assertEquals(PageEdge.NEXT, EdgeScroll.horizontal(390f, 0f, 400f, 32f))
        assertEquals(PageEdge.NONE, EdgeScroll.horizontal(200f, 0f, 400f, 32f))
    }

    @Test
    fun `a finger brushing the edge does not turn the page`() {
        val paging = EdgePaging(delayMillis = 600)

        assertEquals(PageEdge.NONE, paging.onPointer(PageEdge.NEXT, now = 0))
        assertEquals(PageEdge.NONE, paging.onPointer(PageEdge.NEXT, now = 500))
        assertEquals(PageEdge.NONE, paging.onPointer(PageEdge.NONE, now = 550))
        assertEquals(PageEdge.NONE, paging.onPointer(PageEdge.NEXT, now = 700))
    }

    @Test
    fun `a finger held at the edge turns the page, then again after the delay`() {
        val paging = EdgePaging(delayMillis = 600)

        assertEquals(PageEdge.NONE, paging.onPointer(PageEdge.PREVIOUS, now = 1000))
        assertEquals(PageEdge.PREVIOUS, paging.onPointer(PageEdge.PREVIOUS, now = 1600))
        assertEquals(PageEdge.NONE, paging.onPointer(PageEdge.PREVIOUS, now = 2000))
        assertEquals(PageEdge.PREVIOUS, paging.onPointer(PageEdge.PREVIOUS, now = 2200))
    }

    @Test
    fun `holding the middle never turns the page`() {
        val paging = EdgePaging(delayMillis = 600)

        assertEquals(PageEdge.NONE, paging.onPointer(PageEdge.NONE, now = 0))
        assertEquals(PageEdge.NONE, paging.onPointer(PageEdge.NONE, now = 10_000))
    }

    @Test
    fun `changing sides starts the wait again`() {
        val paging = EdgePaging(delayMillis = 600)

        paging.onPointer(PageEdge.PREVIOUS, now = 0)
        assertEquals(PageEdge.NONE, paging.onPointer(PageEdge.NEXT, now = 800))
        assertEquals(PageEdge.NEXT, paging.onPointer(PageEdge.NEXT, now = 1400))
    }
}
