// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OverlapLayoutTest {
    private data class Item(val name: String, val start: Long, val end: Long)

    private fun arrange(vararg items: Item, minLength: Long = 1) =
        OverlapLayout.arrange(items.toList(), { it.start }, { it.end }, minLength)
            .associateBy { it.item.name }

    @Test
    fun `no items, no placements`() {
        assertTrue(OverlapLayout.arrange(emptyList<Item>(), { it.start }, { it.end }).isEmpty())
    }

    @Test
    fun `a lone item takes the whole width`() {
        val placed = arrange(Item("a", 0, 60)).getValue("a")

        assertEquals(Triple(0, 1, 1), Triple(placed.column, placed.columns, placed.span))
    }

    @Test
    fun `two overlapping items sit side by side`() {
        val placed = arrange(Item("a", 0, 60), Item("b", 30, 90))

        assertEquals(0, placed.getValue("a").column)
        assertEquals(1, placed.getValue("b").column)
        assertEquals(2, placed.getValue("a").columns)
        assertEquals(2, placed.getValue("b").columns)
    }

    @Test
    fun `items that touch end to start do not overlap`() {
        val placed = arrange(Item("a", 0, 60), Item("b", 60, 120))

        assertEquals(listOf(0, 0), listOf(placed.getValue("a").column, placed.getValue("b").column))
        assertEquals(
            listOf(1, 1),
            listOf(placed.getValue("a").columns, placed.getValue("b").columns)
        )
    }

    @Test
    fun `a column is reused once its item has ended`() {
        // a and b overlap; c starts after a ends, so it goes back to column 0 under a.
        val placed = arrange(Item("a", 0, 60), Item("b", 30, 120), Item("c", 60, 90))

        assertEquals(0, placed.getValue("c").column)
        assertEquals(2, placed.getValue("c").columns)
    }

    @Test
    fun `a chain is one cluster even if its ends do not overlap`() {
        // a-b and b-c overlap, a-c do not: still two columns for the three of them.
        val placed = arrange(Item("a", 0, 60), Item("b", 30, 120), Item("c", 90, 150))

        assertEquals(setOf(2), placed.values.map { it.columns }.toSet())
        assertEquals(listOf(0, 1, 0), listOf("a", "b", "c").map { placed.getValue(it).column })
    }

    @Test
    fun `separate clusters are laid out independently`() {
        val placed = arrange(
            Item("a", 0, 60),
            Item("b", 30, 90),
            Item("c", 200, 260),
            Item("d", 300, 360)
        )

        assertEquals(2, placed.getValue("a").columns)
        assertEquals(1, placed.getValue("c").columns)
        assertEquals(1, placed.getValue("d").columns)
    }

    @Test
    fun `an item widens over free columns to its right`() {
        // Long one on the left; two short ones stacked in column 1 and 2 do not overlap each other.
        val placed = arrange(
            Item("long", 0, 180),
            Item("x", 0, 60),
            Item("y", 30, 90),
            Item("z", 120, 180)
        )

        assertEquals(3, placed.getValue("x").columns)
        // x (column 1) is blocked on the right by y (column 2), which overlaps it.
        assertEquals(1, placed.getValue("x").span)
        // z (column 1, after x ended) has nothing in column 2 while it lasts: it widens.
        assertEquals(1, placed.getValue("z").column)
        assertEquals(2, placed.getValue("z").span)
        assertEquals(1, placed.getValue("y").span)
        assertEquals(1, placed.getValue("long").span)
    }

    @Test
    fun `longer items come first when they start together`() {
        val placed = arrange(Item("short", 0, 30), Item("long", 0, 120))

        assertEquals(0, placed.getValue("long").column)
        assertEquals(1, placed.getValue("short").column)
    }

    @Test
    fun `equal items keep their input order`() {
        val result = OverlapLayout.arrange(
            listOf(Item("first", 0, 60), Item("second", 0, 60)),
            { it.start },
            { it.end }
        )

        assertEquals(listOf("first", "second"), result.map { it.item.name })
        assertEquals(listOf(0, 1), result.map { it.column })
    }

    @Test
    fun `zero-length items still take room`() {
        val placed = arrange(Item("a", 60, 60), Item("b", 60, 60))

        assertEquals(setOf(0, 1), placed.values.map { it.column }.toSet())
    }

    @Test
    fun `a zero-length item inside another is beside it`() {
        val placed = arrange(Item("long", 0, 120), Item("point", 60, 60), minLength = 15)

        assertEquals(1, placed.getValue("point").column)
        assertEquals(2, placed.getValue("point").columns)
    }

    @Test
    fun `the minimum length can make a short item overlap its neighbour`() {
        val items = arrayOf(Item("a", 0, 10), Item("b", 20, 30))

        assertEquals(1, arrange(*items).getValue("b").columns)
        assertEquals(2, arrange(*items, minLength = 30).getValue("b").columns)
    }

    @Test
    fun `an item that ends before it starts is treated as zero-length`() {
        val placed = arrange(Item("odd", 100, 40), Item("b", 100, 130))

        assertEquals(2, placed.getValue("odd").columns)
    }

    @Test
    fun `a minimum length below one is refused`() {
        assertThrows<IllegalArgumentException> { arrange(Item("a", 0, 1), minLength = 0) }
    }
}
