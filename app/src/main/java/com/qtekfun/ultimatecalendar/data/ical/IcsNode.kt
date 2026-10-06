// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

/**
 * One piece of an iCalendar file. Nodes read from the server keep their original text in
 * [raw], so writing a file back reproduces every untouched byte; nodes built or changed by the
 * app have no raw text and are written in canonical form.
 */
sealed interface IcsNode {
    val raw: String?
}

/** A parameter of a property, such as `VALUE=DATE`. Values are stored unquoted. */
data class IcsParameter(val name: String, val values: List<String>) {
    constructor(name: String, value: String) : this(name, listOf(value))

    val value: String get() = values.joinToString(",")
}

/** A content line, such as `DUE;VALUE=DATE:20261003`. [value] is still escaped. */
data class IcsProperty(
    val name: String,
    val parameters: List<IcsParameter> = emptyList(),
    val value: String,
    override val raw: String? = null
) : IcsNode {
    fun parameter(name: String): IcsParameter? =
        parameters.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

/** A component (`VCALENDAR`, `VTODO`, `VALARM`…) with its properties and subcomponents in order. */
data class IcsComponent(
    val name: String,
    val children: List<IcsNode> = emptyList(),
    val beginRaw: String? = null,
    val endRaw: String? = null
) : IcsNode {
    override val raw: String? get() = null

    val properties: List<IcsProperty> get() = children.filterIsInstance<IcsProperty>()

    val components: List<IcsComponent> get() = children.filterIsInstance<IcsComponent>()

    fun property(name: String): IcsProperty? =
        properties.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun components(name: String): List<IcsComponent> =
        components.filter { it.name.equals(name, ignoreCase = true) }

    /**
     * Replaces the first property called [name] in place, removing any others with that name,
     * or adds it before the subcomponents when there was none. A null [property] removes them.
     */
    fun withProperty(name: String, property: IcsProperty?): IcsComponent {
        val index = children.indexOfFirst { it is IcsProperty && it.name.equals(name, true) }
        val others = children.filterNot { it is IcsProperty && it.name.equals(name, true) }
        val updated = when {
            property == null -> others

            index >= 0 -> others.toMutableList().apply { add(index, property) }

            else -> {
                val firstComponent = others.indexOfFirst { it is IcsComponent }
                val at = if (firstComponent < 0) others.size else firstComponent
                others.toMutableList().apply { add(at, property) }
            }
        }
        return copy(children = updated)
    }

    /** Replaces the subcomponents called [name] with [replacement], keeping everything else. */
    fun withComponents(name: String, replacement: List<IcsComponent>): IcsComponent {
        val index = children.indexOfFirst { it is IcsComponent && it.name.equals(name, true) }
        val others = children.filterNot { it is IcsComponent && it.name.equals(name, true) }
        val at = if (index >= 0) index else others.size
        return copy(children = others.toMutableList().apply { addAll(at, replacement) })
    }
}
