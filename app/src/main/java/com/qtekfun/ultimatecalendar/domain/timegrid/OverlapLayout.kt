// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

/**
 * An item placed side by side with the items it overlaps: it sits in [column] of [columns] (the
 * columns of its whole cluster) and may widen over the free columns to its right, [span]
 * columns wide in all.
 */
data class Placement<T>(val item: T, val column: Int, val columns: Int, val span: Int)

/**
 * Lays out overlapping items in columns, as Google Calendar does. Pure and generic: the items are
 * anything with a start and an end on one axis (minutes of a day, or days), so Day, 3 days, Week
 * and the all-day strip share it.
 */
object OverlapLayout {
    private class Entry<T>(val item: T, val start: Long, val end: Long) {
        var column = 0

        fun overlaps(other: Entry<T>) = start < other.end && other.start < end
    }

    /**
     * Places [items] in clusters of overlapping items. Items that only touch (one ends where the
     * next starts) do not overlap. An item shorter than [minLength] counts as [minLength] long,
     * so zero-length items take room like any other. The result is ordered by start, longer
     * first, then by input order.
     */
    fun <T> arrange(
        items: List<T>,
        start: (T) -> Long,
        end: (T) -> Long,
        minLength: Long = 1
    ): List<Placement<T>> {
        require(minLength >= 1) { "Items need some length to take room" }
        val entries = items
            .map { Entry(it, start(it), maxOf(end(it), start(it) + minLength)) }
            .sortedWith(compareBy<Entry<T>> { it.start }.thenByDescending { it.end })
        val placed = mutableListOf<Placement<T>>()
        var cluster = mutableListOf<Entry<T>>()
        var clusterEnd = Long.MIN_VALUE
        for (entry in entries) {
            if (cluster.isNotEmpty() && entry.start >= clusterEnd) {
                placed += place(cluster)
                cluster = mutableListOf()
            }
            cluster += entry
            clusterEnd = if (cluster.size == 1) entry.end else maxOf(clusterEnd, entry.end)
        }
        if (cluster.isNotEmpty()) placed += place(cluster)
        return placed
    }

    /** Greedy: each item takes the first column that is free when it starts. */
    private fun <T> place(cluster: List<Entry<T>>): List<Placement<T>> {
        val columnEnds = mutableListOf<Long>()
        for (entry in cluster) {
            val free = columnEnds.indexOfFirst { it <= entry.start }
            if (free == -1) {
                entry.column = columnEnds.size
                columnEnds += entry.end
            } else {
                entry.column = free
                columnEnds[free] = entry.end
            }
        }
        val columns = columnEnds.size
        return cluster.map { entry ->
            var span = 1
            while (entry.column + span < columns &&
                cluster.none { it.column == entry.column + span && it.overlaps(entry) }
            ) {
                span++
            }
            Placement(entry.item, entry.column, columns, span)
        }
    }
}
