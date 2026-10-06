// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

/**
 * Replaces every property called [name] with [replacement]: the new ones take the place of the
 * first old one, or go before the subcomponents when there was none. Everything else is kept.
 */
internal fun IcsComponent.withProperties(
    name: String,
    replacement: List<IcsProperty>
): IcsComponent {
    val matches = { node: IcsNode -> node is IcsProperty && node.name.equals(name, true) }
    val index = children.indexOfFirst(matches)
    val others = children.filterNot(matches)
    val at = when {
        index >= 0 -> index
        else -> others.indexOfFirst { it is IcsComponent }.let { if (it < 0) others.size else it }
    }
    return copy(children = others.toMutableList().apply { addAll(at, replacement) })
}

/**
 * Pairs each new item with the existing node that already stands for it, so that what was not
 * touched keeps its original text. [existing] are the nodes with what they were read as (null
 * when the app cannot read them: those are always kept, after the others).
 */
internal fun <T, N : IcsNode> reconcile(
    existing: List<Pair<N, T?>>,
    wanted: List<T>,
    create: (T) -> N
): List<N> {
    val pool = existing.filter { it.second != null }.toMutableList()
    val matched = wanted.map { item ->
        val index = pool.indexOfFirst { it.second == item }
        if (index >= 0) pool.removeAt(index).first else create(item)
    }
    return matched + existing.filter { it.second == null }.map { it.first }
}
